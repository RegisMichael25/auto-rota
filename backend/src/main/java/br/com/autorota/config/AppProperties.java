package br.com.autorota.config;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(@Valid @NotNull External external, @Valid @NotNull Cors cors) {

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

	public record Osrm(@NotBlank String baseUrl, @NotBlank String profile) {
	}

	public record Cors(@NotEmpty List<String> allowedOrigins) {
	}

}
