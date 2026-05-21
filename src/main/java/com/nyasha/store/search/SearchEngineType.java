package com.nyasha.store.search;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public enum SearchEngineType {
    SQL_LIKE("sql_like", Set.of("sql", "sql-like", "sqllike")),
    POSTGRES_FTS("postgres_fts", Set.of("pg_fts", "postgres-full-text", "postgresfts", "fts")),
    IN_MEMORY("in_memory", Set.of("memory", "in-memory", "inmemory")),
    OPENSEARCH("opensearch", Set.of("os", "open-search"));

    private final String canonical;
    private final Set<String> aliases;

    SearchEngineType(String canonical, Set<String> aliases) {
        this.canonical = canonical;
        this.aliases = aliases;
    }

    public String canonical() {
        return canonical;
    }

    public static SearchEngineType from(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return IN_MEMORY;
        }

        return Arrays.stream(values())
                .filter(type -> type.matches(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported search engine: " + value));
    }

    private boolean matches(String normalized) {
        if (canonical.equals(normalized)) {
            return true;
        }
        return aliases.stream().anyMatch(alias -> Objects.equals(alias, normalized));
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
