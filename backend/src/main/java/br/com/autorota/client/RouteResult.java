package br.com.autorota.client;

import java.util.List;

/**
 * Resultado do OSRM /route.
 *
 * @param geometryGeoJson LineString GeoJSON da rota completa (lng, lat)
 * @param legs um trecho por par consecutivo de coordenadas
 */
public record RouteResult(String geometryGeoJson, double distanceM, double durationS, List<Leg> legs) {

	public record Leg(double distanceM, double durationS) {
	}

}
