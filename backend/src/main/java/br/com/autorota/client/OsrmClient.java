package br.com.autorota.client;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.autorota.config.AppProperties;
import br.com.autorota.domain.ExternalServiceUnavailableException;
import br.com.autorota.domain.RouteNotFoundException;

/** Cliente do OSRM: matriz de distâncias/durações (/table) e geometria da rota (/route). */
@Component
public class OsrmClient {

	private static final String SERVICE = "OSRM";

	/** Códigos do OSRM que significam "não há rota", não falha do serviço. */
	private static final Set<String> NO_ROUTE_CODES = Set.of("NoRoute", "NoSegment", "NoTable", "NoMatch");

	private final RestClient http;

	private final ObjectMapper objectMapper;

	private final String baseUrl;

	private final String profile;

	private final int snapRadiusM;

	public OsrmClient(RestClient.Builder builder, AppProperties properties, ObjectMapper objectMapper) {
		var osrm = properties.external().osrm();
		this.http = builder.clone().build();
		this.baseUrl = osrm.baseUrl().replaceAll("/+$", "");
		this.objectMapper = objectMapper;
		this.profile = osrm.profile();
		this.snapRadiusM = osrm.snapRadiusM();
	}

	public DistanceMatrix table(Coordinates origin, List<Coordinates> destinations) {
		if (destinations == null || destinations.isEmpty()) {
			throw new IllegalArgumentException("Informe ao menos um destino");
		}
		List<Coordinates> points = new ArrayList<>(destinations.size() + 1);
		points.add(origin);
		points.addAll(destinations);

		JsonNode body = call("table", "annotations=distance,duration", points);
		double[][] distances = readMatrix(body.path("distances"), points.size(), "distances");
		double[][] durations = readMatrix(body.path("durations"), points.size(), "durations");
		return new DistanceMatrix(distances, durations);
	}

	public RouteResult route(List<Coordinates> coordinates) {
		if (coordinates == null || coordinates.size() < 2) {
			throw new IllegalArgumentException("A rota precisa de ao menos 2 coordenadas");
		}
		JsonNode body = call("route", "overview=full&geometries=geojson&steps=false", coordinates);

		JsonNode route = body.path("routes").path(0);
		if (route.isMissingNode() || !route.path("geometry").isObject()) {
			throw new ExternalServiceUnavailableException(SERVICE,
					new IllegalStateException("Resposta do /route sem rota ou sem geometria GeoJSON"));
		}
		List<RouteResult.Leg> legs = new ArrayList<>();
		for (JsonNode leg : route.path("legs")) {
			legs.add(new RouteResult.Leg(leg.path("distance").asDouble(), leg.path("duration").asDouble()));
		}
		return new RouteResult(toJson(route.path("geometry")), route.path("distance").asDouble(),
				route.path("duration").asDouble(), legs);
	}

	/**
	 * A URI é montada à mão: o OSRM exige ';' e ',' literais no caminho, que o template do RestClient codificaria.
	 * Todos os trechos variáveis são números formatados ou valores de configuração.
	 */
	private JsonNode call(String service, String query, List<Coordinates> points) {
		String coordinates = points.stream().map(OsrmClient::format).collect(Collectors.joining(";"));
		String radiuses = String.join(";", Collections.nCopies(points.size(), String.valueOf(snapRadiusM)));
		URI uri = URI.create(baseUrl + "/" + service + "/v1/" + profile + "/" + coordinates + "?" + query
				+ "&radiuses=" + radiuses);
		JsonNode body;
		try {
			body = http.get().uri(uri).retrieve().body(JsonNode.class);
		}
		catch (HttpStatusCodeException ex) {
			// O OSRM responde 400 com {"code": "NoSegment", ...} quando um ponto não tem via dentro do raio.
			body = parseQuietly(ex.getResponseBodyAsString());
			if (body == null || !NO_ROUTE_CODES.contains(body.path("code").asText())) {
				throw new ExternalServiceUnavailableException(SERVICE, ex);
			}
		}
		catch (RestClientException ex) {
			throw new ExternalServiceUnavailableException(SERVICE, ex);
		}

		String code = body == null ? "" : body.path("code").asText();
		if (NO_ROUTE_CODES.contains(code)) {
			throw new RouteNotFoundException(noRouteMessage(code, body.path("message").asText("")));
		}
		if (!"Ok".equals(code)) {
			throw new ExternalServiceUnavailableException(SERVICE,
					new IllegalStateException("OSRM respondeu code=" + code));
		}
		return body;
	}

	/** Entradas nulas na matriz significam par sem ligação por estrada. */
	private static double[][] readMatrix(JsonNode rows, int size, String name) {
		if (!rows.isArray() || rows.size() != size) {
			throw new ExternalServiceUnavailableException(SERVICE,
					new IllegalStateException("Matriz '" + name + "' ausente ou com tamanho inesperado"));
		}
		double[][] matrix = new double[size][size];
		for (int i = 0; i < size; i++) {
			JsonNode row = rows.get(i);
			for (int j = 0; j < size; j++) {
				JsonNode cell = row.path(j);
				if (cell.isNull() || cell.isMissingNode()) {
					throw new RouteNotFoundException("Não há rota terrestre entre os pontos " + label(i) + " e "
							+ label(j) + ".");
				}
				matrix[i][j] = cell.asDouble();
			}
		}
		return matrix;
	}

	private static String label(int index) {
		return index == 0 ? "origem" : "destino " + index;
	}

	private String noRouteMessage(String code, String detail) {
		if ("NoSegment".equals(code)) {
			return "Algum ponto não tem via acessível a até " + snapRadiusM + " m. Confira o CEP e o número.";
		}
		return "Não há rota terrestre entre os pontos informados." + (detail.isBlank() ? "" : " (" + detail + ")");
	}

	/** OSRM espera longitude,latitude. */
	private static String format(Coordinates c) {
		return String.format(Locale.ROOT, "%.6f,%.6f", c.lng(), c.lat());
	}

	private JsonNode parseQuietly(String json) {
		try {
			return json == null || json.isBlank() ? null : objectMapper.readTree(json);
		}
		catch (Exception ex) {
			return null;
		}
	}

	private String toJson(JsonNode node) {
		try {
			return objectMapper.writeValueAsString(node);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Falha ao serializar a geometria", ex);
		}
	}

}
