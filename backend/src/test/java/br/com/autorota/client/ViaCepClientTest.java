package br.com.autorota.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.com.autorota.PostgresIntegrationTest;
import br.com.autorota.domain.CepNotFoundException;
import br.com.autorota.domain.ExternalServiceUnavailableException;
import br.com.autorota.domain.InvalidCepException;
import br.com.autorota.repository.CepCacheRepository;

class ViaCepClientTest extends PostgresIntegrationTest {

	private static final String PATH = "/viacep/ws/01001000/json/";

	private static final String SE = """
			{"cep": "01001-000", "logradouro": "Praça da Sé", "complemento": "lado ímpar", "unidade": "",
			 "bairro": "Sé", "localidade": "São Paulo", "uf": "SP", "estado": "São Paulo", "ibge": "3550308"}
			""";

	@Autowired
	private ViaCepClient client;

	@Autowired
	private CepCacheRepository cache;

	@BeforeEach
	void clearCache() {
		cache.deleteAll();
	}

	@Test
	void returnsAddressAndNormalizesCep() {
		WIREMOCK.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(SE)));

		var address = client.lookup("01001-000");

		assertThat(address).isEqualTo(
				new CepAddress("01001000", "Praça da Sé", "lado ímpar", "Sé", "São Paulo", "SP"));
	}

	@Test
	void repeatedLookupHitsCacheInsteadOfViaCep() {
		WIREMOCK.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(SE)));

		client.lookup("01001000");
		var second = client.lookup("01001-000");

		assertThat(second.city()).isEqualTo("São Paulo");
		WIREMOCK.verify(1, getRequestedFor(urlPathEqualTo(PATH)));
	}

	@Test
	void unknownCepThrowsAndIsCachedAsNotFound() {
		WIREMOCK.stubFor(get(urlPathEqualTo("/viacep/ws/99999999/json/")).willReturn(okJson("{\"erro\": \"true\"}")));

		assertThatThrownBy(() -> client.lookup("99999999")).isInstanceOf(CepNotFoundException.class);
		assertThatThrownBy(() -> client.lookup("99999-999")).isInstanceOf(CepNotFoundException.class);

		WIREMOCK.verify(1, getRequestedFor(urlPathEqualTo("/viacep/ws/99999999/json/")));
	}

	@Test
	void acceptsBooleanErro() {
		WIREMOCK.stubFor(get(urlPathEqualTo("/viacep/ws/99999998/json/")).willReturn(okJson("{\"erro\": true}")));

		assertThatThrownBy(() -> client.lookup("99999998")).isInstanceOf(CepNotFoundException.class);
	}

	@Test
	void invalidCepFailsWithoutCallingViaCep() {
		assertThatThrownBy(() -> client.lookup("123")).isInstanceOf(InvalidCepException.class);
		assertThatThrownBy(() -> client.lookup(null)).isInstanceOf(InvalidCepException.class);

		WIREMOCK.verify(0, anyRequestedFor(anyUrl()));
	}

	@Test
	void serverErrorBecomesDomainErrorAndIsNotCached() {
		WIREMOCK.stubFor(get(urlPathEqualTo(PATH)).willReturn(serverError()));

		assertThatThrownBy(() -> client.lookup("01001000")).isInstanceOf(ExternalServiceUnavailableException.class);
		assertThatThrownBy(() -> client.lookup("01001000")).isInstanceOf(ExternalServiceUnavailableException.class);

		WIREMOCK.verify(2, getRequestedFor(urlPathEqualTo(PATH)));
		assertThat(cache.count()).isZero();
	}

	@Test
	void responseWithoutCityIsTreatedAsOutageAndNotCached() {
		WIREMOCK.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson("{\"cep\": \"01001-000\", \"logradouro\": \"Praça da Sé\"}")));

		assertThatThrownBy(() -> client.lookup("01001000")).isInstanceOf(ExternalServiceUnavailableException.class);
		assertThat(cache.count()).isZero();
	}

	@Test
	void nonJsonResponseIsTreatedAsOutage() {
		WIREMOCK.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(200)
			.withHeader("Content-Type", "text/html")
			.withBody("<html>manutenção</html>")));

		assertThatThrownBy(() -> client.lookup("01001000")).isInstanceOf(ExternalServiceUnavailableException.class);
	}

	@Test
	void slowResponseTimesOutAsDomainError() {
		WIREMOCK.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withFixedDelay(1500)
			.withHeader("Content-Type", "application/json")
			.withBody(SE)));

		assertThatThrownBy(() -> client.lookup("01001000")).isInstanceOf(ExternalServiceUnavailableException.class);
	}

}
