package br.com.autorota.service;

import org.springframework.stereotype.Service;

import br.com.autorota.client.BrasilApiClient;
import br.com.autorota.client.CepAddress;
import br.com.autorota.client.Coordinates;
import br.com.autorota.client.NominatimClient;
import br.com.autorota.client.ViaCepClient;
import br.com.autorota.domain.Address;
import br.com.autorota.domain.AddressNotGeocodedException;
import br.com.autorota.domain.CoordSource;

/** Resolve CEP + número em endereço com coordenadas: ViaCEP → BrasilAPI → Nominatim (só se faltar coordenada). */
@Service
public class AddressResolutionService {

	private final ViaCepClient viaCep;

	private final BrasilApiClient brasilApi;

	private final NominatimClient nominatim;

	public AddressResolutionService(ViaCepClient viaCep, BrasilApiClient brasilApi, NominatimClient nominatim) {
		this.viaCep = viaCep;
		this.brasilApi = brasilApi;
		this.nominatim = nominatim;
	}

	public CepAddress lookupCep(String cep) {
		return viaCep.lookup(cep);
	}

	public ResolvedAddress resolve(String cep, String number) {
		if (number == null || number.isBlank()) {
			throw new IllegalArgumentException("Número do endereço é obrigatório");
		}
		CepAddress found = viaCep.lookup(cep);

		var fromBrasilApi = brasilApi.coordinates(found.cep());
		if (fromBrasilApi.isPresent()) {
			return toResolved(found, number, fromBrasilApi.get(), CoordSource.BRASILAPI);
		}

		Coordinates coordinates = nominatim.geocode(found.street(), number, found.city(), found.uf())
			.orElseThrow(() -> new AddressNotGeocodedException(found.cep(), number));
		return toResolved(found, number, coordinates, CoordSource.NOMINATIM);
	}

	private static ResolvedAddress toResolved(CepAddress found, String number, Coordinates coordinates,
			CoordSource source) {
		var address = new Address(found.cep(), found.street(), number.trim(), found.complement(),
				found.neighborhood(), found.city(), found.uf(), coordinates.lat(), coordinates.lng());
		return new ResolvedAddress(address, source);
	}

}
