package br.com.autorota.service;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.com.autorota.client.BrasilApiClient;
import br.com.autorota.client.CepAddress;
import br.com.autorota.client.Coordinates;
import br.com.autorota.client.NominatimClient;
import br.com.autorota.client.ViaCepClient;
import br.com.autorota.config.AppProperties;
import br.com.autorota.config.AppProperties.GeocodingStrategy;
import br.com.autorota.domain.Address;
import br.com.autorota.domain.AddressNotGeocodedException;
import br.com.autorota.domain.CoordSource;
import br.com.autorota.domain.ExternalServiceUnavailableException;

/**
 * Resolve CEP + número em endereço com coordenadas. O endereço vem sempre do ViaCEP; a ordem dos provedores de
 * coordenadas segue {@code app.geocoding.strategy}:
 * <ul>
 * <li>{@code BRASILAPI_FIRST}: BrasilAPI → Nominatim (só se faltar coordenada).</li>
 * <li>{@code NOMINATIM_FIRST}: Nominatim (logradouro + número) → BrasilAPI. Se o CEP não tiver logradouro (CEP geral
 * de cidade), o Nominatim só daria o centro da cidade, então a BrasilAPI vem primeiro.</li>
 * </ul>
 */
@Service
public class AddressResolutionService {

	private static final Logger log = LoggerFactory.getLogger(AddressResolutionService.class);

	private final ViaCepClient viaCep;

	private final BrasilApiClient brasilApi;

	private final NominatimClient nominatim;

	private final GeocodingStrategy strategy;

	public AddressResolutionService(ViaCepClient viaCep, BrasilApiClient brasilApi, NominatimClient nominatim,
			AppProperties properties) {
		this.viaCep = viaCep;
		this.brasilApi = brasilApi;
		this.nominatim = nominatim;
		this.strategy = properties.geocoding().strategy();
	}

	public CepAddress lookupCep(String cep) {
		return viaCep.lookup(cep);
	}

	public ResolvedAddress resolve(String cep, String number) {
		if (number == null || number.isBlank()) {
			throw new IllegalArgumentException("Número do endereço é obrigatório");
		}
		CepAddress found = viaCep.lookup(cep);
		String trimmedNumber = number.trim();

		boolean nominatimFirst = strategy == GeocodingStrategy.NOMINATIM_FIRST && found.street() != null;
		return nominatimFirst ? nominatimThenBrasilApi(found, trimmedNumber) : brasilApiThenNominatim(found, trimmedNumber);
	}

	private ResolvedAddress brasilApiThenNominatim(CepAddress found, String number) {
		var fromBrasilApi = brasilApi.coordinates(found.cep());
		if (fromBrasilApi.isPresent()) {
			return toResolved(found, number, fromBrasilApi.get(), CoordSource.BRASILAPI);
		}
		Coordinates coordinates = nominatim.geocode(found.street(), number, found.neighborhood(), found.city(), found.uf(),
				found.cep())
			.orElseThrow(() -> new AddressNotGeocodedException(found.cep(), number));
		return toResolved(found, number, coordinates, CoordSource.NOMINATIM);
	}

	/** Falha do Nominatim não interrompe: tenta a BrasilAPI e só repassa a falha se ela também não tiver coordenada. */
	private ResolvedAddress nominatimThenBrasilApi(CepAddress found, String number) {
		Optional<Coordinates> fromNominatim;
		ExternalServiceUnavailableException nominatimFailure = null;
		try {
			fromNominatim = nominatim.geocode(found.street(), number, found.neighborhood(), found.city(), found.uf(),
				found.cep());
		}
		catch (ExternalServiceUnavailableException ex) {
			log.warn("Nominatim indisponível para o CEP {}; tentando BrasilAPI", found.cep());
			fromNominatim = Optional.empty();
			nominatimFailure = ex;
		}
		if (fromNominatim.isPresent()) {
			return toResolved(found, number, fromNominatim.get(), CoordSource.NOMINATIM);
		}

		var fromBrasilApi = brasilApi.coordinates(found.cep());
		if (fromBrasilApi.isPresent()) {
			return toResolved(found, number, fromBrasilApi.get(), CoordSource.BRASILAPI);
		}
		if (nominatimFailure != null) {
			throw nominatimFailure;
		}
		throw new AddressNotGeocodedException(found.cep(), number);
	}

	private static ResolvedAddress toResolved(CepAddress found, String number, Coordinates coordinates,
			CoordSource source) {
		var address = new Address(found.cep(), found.street(), number, found.complement(), found.neighborhood(),
				found.city(), found.uf(), coordinates.lat(), coordinates.lng());
		return new ResolvedAddress(address, source);
	}

}
