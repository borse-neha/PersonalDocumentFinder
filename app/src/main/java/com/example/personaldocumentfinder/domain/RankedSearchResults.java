package com.example.personaldocumentfinder.domain;

import com.example.personaldocumentfinder.data.DocumentEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class RankedSearchResults {
    private final List<DocumentEntity> primaryMatches;
    private final List<DocumentEntity> mentionMatches;

    public RankedSearchResults(List<DocumentEntity> primaryMatches, List<DocumentEntity> mentionMatches) {
        this.primaryMatches = primaryMatches != null ? Collections.unmodifiableList(new ArrayList<>(primaryMatches)) : Collections.emptyList();
        this.mentionMatches = mentionMatches != null ? Collections.unmodifiableList(new ArrayList<>(mentionMatches)) : Collections.emptyList();
    }

    public RankedSearchResults() {
        this(Collections.emptyList(), Collections.emptyList());
    }

    public List<DocumentEntity> getPrimaryMatches() {
        return primaryMatches;
    }

    public List<DocumentEntity> getMentionMatches() {
        return mentionMatches;
    }

    public List<DocumentEntity> getAllRanked() {
        List<DocumentEntity> all = new ArrayList<>(primaryMatches.size() + mentionMatches.size());
        all.addAll(primaryMatches);
        all.addAll(mentionMatches);
        return Collections.unmodifiableList(all);
    }

    public int getTotalCount() {
        return primaryMatches.size() + mentionMatches.size();
    }

    public boolean isEmpty() {
        return primaryMatches.isEmpty() && mentionMatches.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RankedSearchResults that = (RankedSearchResults) o;
        return Objects.equals(primaryMatches, that.primaryMatches) &&
                Objects.equals(mentionMatches, that.mentionMatches);
    }

    @Override
    public int hashCode() {
        return Objects.hash(primaryMatches, mentionMatches);
    }

    @Override
    public String toString() {
        return "RankedSearchResults{" +
                "primaryMatches=" + primaryMatches.size() +
                ", mentionMatches=" + mentionMatches.size() +
                '}';
    }
}
