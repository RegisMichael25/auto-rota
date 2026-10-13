package br.com.autorota.config;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import br.com.autorota.optimizer.OptimizationMetric;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(@Valid @NotNull External external, @Valid @NotNull Geocoding geocoding,
		@Valid @NotNull Optimizer optimizer, @Valid @NotNull Fuel fuel, @Valid @NotNull Cors cors) {

	public record External(
			@Valid @NotNull Endpoint viacep,
			@Valid @NotNull Endpoint brasilapi,
			@Valid @NotNull Nominatim nominatim,
			@Valid @NotNull Osrm osrm) {
	}

	public record Endpoint(@NotBlank String baseUrl) {
	}

	public record Nominatim(@NotBlank String baseUrl, @NotBlank String userAgent, @PositiveOrZero long minIntervalMs) {
	}

	/**
	 * @param snapRadiusM raio máximo (m) para encaixar cada ponto numa via; além dele o ponto é tratado como sem
	 * acesso por estrada (sem isso o OSRM encaixa em vias a centenas de km).
	 */
	public record Osrm(@NotBlank String baseUrl, @NotBlank String profile, @Positive int snapRadiusM) {
	}

	public record Geocoding(@NotNull GeocodingStrategy strategy) {
	}

	/**
	 * Ordem dos provedores de coordenadas. A BrasilAPI dá uma coordenada por CEP (às vezes aproximada, sem considerar
	 * o número); o Nominatim geocodifica logradouro + número, mas é limitado a 1 req/s.
	 */
	public enum GeocodingStrategy {
		BRASILAPI_FIRST, NOMINATIM_FIRST
	}

	/** Métrica minimizada na ordem de visita. */
	public record Optimizer(@NotNull OptimizationMetric metric) {
	}

	/** Premissas de combustível usadas quando a requisição não informa as suas. */
	public record Fuel(@NotNull @Positive BigDecimal defaultConsumptionKmPerL,
			@NotNull @Positive BigDecimal defaultPricePerL) {
	}

	public record Cors(@NotEmpty List<String> allowedOrigins) {
	}

}
