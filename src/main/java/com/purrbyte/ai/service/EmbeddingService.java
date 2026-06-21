package com.purrbyte.ai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper over the transformer {@link EmbeddingModel}. The multilingual-e5 family requires the
 * {@code "passage: "} / {@code "query: "} prefixes to produce comparable embeddings; omitting them
 * silently degrades ranking quality.
 */
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private static final String PASSAGE_PREFIX = "passage: ";
    private static final String QUERY_PREFIX = "query: ";

    private final EmbeddingModel embeddingModel;

    public float[] embedPassage(String text) {
        return embeddingModel.embed(PASSAGE_PREFIX + text);
    }

    public float[] embedQuery(String query) {
        return embeddingModel.embed(QUERY_PREFIX + query);
    }
}
