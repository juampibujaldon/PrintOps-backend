package com.printops.demo.dto;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;

import java.util.*;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

// springdoc nombra los esquemas OpenAPI por el nombre simple de la clase: dos DTOs (o
// records anidados) con el mismo nombre se pisan y el front genera tipos equivocados.
class DtoNamesTest {

    @Test
    void dtoSimpleNamesAreUnique() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*")));
        Map<String, List<String>> byName = new TreeMap<>();
        Deque<Class<?>> pending = new ArrayDeque<>();
        for (var bd : scanner.findCandidateComponents("com.printops.demo.dto")) {
            pending.add(Class.forName(bd.getBeanClassName()));
        }
        Set<Class<?>> seen = new HashSet<>();
        while (!pending.isEmpty()) {
            Class<?> c = pending.pop();
            if (!seen.add(c)) continue;
            byName.computeIfAbsent(c.getSimpleName(), k -> new ArrayList<>()).add(c.getName());
            pending.addAll(Arrays.asList(c.getDeclaredClasses()));
        }
        assertThat(byName.entrySet()).filteredOn(e -> e.getValue().size() > 1).isEmpty();
    }
}
