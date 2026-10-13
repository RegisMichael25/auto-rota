package br.com.autorota.web.dto;

import java.util.List;

import org.springframework.data.domain.Page;

/** Página com formato estável (o JSON de {@code PageImpl} não é garantido entre versões). */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

	public static <T> PageResponse<T> of(Page<T> page) {
		return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
				page.getTotalPages());
	}

}
