package com.example.personaldocumentfinder.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SearchIntent {
    private final String rawQuery;
    private final String normalizedQuery;
    private final List<String> targetDocumentTypes;
    private final List<String> targetCategories;
    private final List<String> synonyms;

    public SearchIntent(
            String rawQuery,
            String normalizedQuery,
            List<String> targetDocumentTypes,
            List<String> targetCategories,
            List<String> synonyms
    ) {
        this.rawQuery = rawQuery != null ? rawQuery : "";
        this.normalizedQuery = normalizedQuery != null ? normalizedQuery : "";
        this.targetDocumentTypes = targetDocumentTypes != null ? Collections.unmodifiableList(new ArrayList<>(targetDocumentTypes)) : Collections.emptyList();
        this.targetCategories = targetCategories != null ? Collections.unmodifiableList(new ArrayList<>(targetCategories)) : Collections.emptyList();
        this.synonyms = synonyms != null ? Collections.unmodifiableList(new ArrayList<>(synonyms)) : Collections.emptyList();
    }

    public SearchIntent(String rawQuery, String normalizedQuery) {
        this(rawQuery, normalizedQuery, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    public String getRawQuery() {
        return rawQuery;
    }

    public String getNormalizedQuery() {
        return normalizedQuery;
    }

    public List<String> getTargetDocumentTypes() {
        return targetDocumentTypes;
    }

    public List<String> getTargetCategories() {
        return targetCategories;
    }

    public List<String> getSynonyms() {
        return synonyms;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SearchIntent that = (SearchIntent) o;
        return Objects.equals(rawQuery, that.rawQuery) &&
                Objects.equals(normalizedQuery, that.normalizedQuery) &&
                Objects.equals(targetDocumentTypes, that.targetDocumentTypes) &&
                Objects.equals(targetCategories, that.targetCategories) &&
                Objects.equals(synonyms, that.synonyms);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rawQuery, normalizedQuery, targetDocumentTypes, targetCategories, synonyms);
    }

    @Override
    public String toString() {
        return "SearchIntent{" +
                "rawQuery='" + rawQuery + '\'' +
                ", normalizedQuery='" + normalizedQuery + '\'' +
                ", targetDocumentTypes=" + targetDocumentTypes +
                ", targetCategories=" + targetCategories +
                ", synonyms=" + synonyms +
                '}';
    }
}
