package com.printops.demo.dto;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.function.Function;

// Página de resultados con forma estable para el front (Page de Spring serializa campos
// internos que cambian entre versiones).
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages, boolean last) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    public static Pageable request(Integer page, Integer size) {
        int p = page != null && page >= 0 ? page : 0;
        int s = size != null && size > 0 ? Math.min(size, MAX_SIZE) : DEFAULT_SIZE;
        return PageRequest.of(p, s);
    }
}
