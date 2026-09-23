package com.hawkify.common;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

public record PaginaResponse<T>(
        List<T> contenido,
        int pagina,
        int tamano,
        long totalElementos,
        int totalPaginas
) {
    public static <E, T> PaginaResponse<T> de(Page<E> page, Function<E, T> mapper) {
        return new PaginaResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    public static <T> PaginaResponse<T> de(List<T> contenido, int pagina, int tamano, long total) {
        int paginas = tamano == 0 ? 0 : (int) Math.ceil((double) total / tamano);
        return new PaginaResponse<>(contenido, pagina, tamano, total, paginas);
    }
}
