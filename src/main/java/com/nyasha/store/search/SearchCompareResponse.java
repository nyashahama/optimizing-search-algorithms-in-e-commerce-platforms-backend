package com.nyasha.store.search;

import java.util.List;

public record SearchCompareResponse(String query, int limit, List<SearchResult> results) {
}
