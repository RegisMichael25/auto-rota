package br.com.autorota.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import br.com.autorota.config.AppProperties;
import br.com.autorota.domain.CacheKeys;
import br.com.autorota.domain.Cep;
import br.com.autorota.domain.CepCache;
import br.com.autorota.domain.CepNotFoundException;
import br.com.autorota.domain.ExternalServiceUnavailableException;
import br.com.autorota.repository.CepCacheRepository;

/** Consulta de endereço por CEP no ViaCEP, com cache persistente (inclusive CEP inexistente). */
@Component
public class ViaCepClient {

	private static final Logger log = LoggerFactory.getLogger(ViaCepClient.class);

	private static final String SERVICE = "ViaCEP";

	private final RestClient http;

	private final CepCacheRepository cache;

	public ViaCepClient(RestClient.Builder builder, AppProperties properties, CepCacheRepository cache) {
		this.http = builder.clone().baseUrl(properties.external().viacep().baseUrl()).build();
		this.cache = cache;
	}

	/**
	 * @throws br.com.autorota.domain.InvalidCepException se o CEP não tiver 8 dígitos
	 * @throws CepNotFoundException se o ViaCEP não conhecer o CEP
	 * @throws ExternalServiceUnavailableException se o ViaCEP falhar
	 */
	public CepAddress lookup(String rawCep) {
		String cep = Cep.normalize(rawCep);
		String key = CacheKeys.sha256(cep);

		var cached = cache.findById(key);
		if (cached.isPresent()) {
			return toAddress(cached.get());
		}

		ViaCepResponse response = fetch(cep);
		CepCache entry = (response == null || Boolean.TRUE.equals(response.erro())) ? CepCache.notFound(key, cep)
				: new CepCache(key, cep, true, blankToNull(response.logradouro()), blankToNull(response.complemento()),
						blankToNull(response.bairro()), response.localidade(), response.uf());
		save(entry);
		return toAddress(entry);
	}

	private ViaCepResponse fetch(String cep) {
		try {
			return http.get().uri("/{cep}/json/", cep).retrieve().body(ViaCepResponse.class);
		}
		catch (RestClientException ex) {
			throw new ExternalServiceUnavailableException(SERVICE, ex);
		}
	}

	private void save(CepCache entry) {
		try {
			cache.save(entry);
		}
		catch (DataIntegrityViolationException ex) {
			// Outra requisição gravou o mesmo CEP em paralelo; o valor é o mesmo.
			log.debug("cep_cache já contém {}", entry.getCep());
		}
	}

	private static CepAddress toAddress(CepCache entry) {
		if (!entry.isFound()) {
			throw new CepNotFoundException(entry.getCep());
		}
		return new CepAddress(entry.getCep(), entry.getStreet(), entry.getComplement(), entry.getNeighborhood(),
				entry.getCity(), entry.getUf());
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.trim();
	}

	/** O ViaCEP devolve {@code "erro": true} ou {@code "erro": "true"} para CEP inexistente. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record ViaCepResponse(String cep, String logradouro, String complemento, String bairro, String localidade,
			String uf, Boolean erro) {
	}

}
