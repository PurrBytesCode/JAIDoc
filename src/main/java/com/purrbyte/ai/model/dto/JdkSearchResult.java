package com.purrbyte.ai.model.dto;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
/**
 * Search result from a vector kNN query on {@code JdkDocChunk}.
 */
public final class JdkSearchResult {

    /**
     * Chunk identifier (e.g. {@code java.io.BufferedInputStream#read(byte[],int,int)}).
     */
    private String chunkId;

    /**
     * Element kind (e.g. {@code CLASS}, {@code METHOD}, {@code FIELD}).
     */
    private String kind;

    /**
     * Fully qualified name of the element (e.g. {@code java.io.BufferedInputStream}).
     */
    private String qualifiedType;

    /**
     * Member name (e.g. {@code read}), empty string or {@code null} for type-level elements.
     */
    private String member;

    /**
     * Signature of the element (e.g. {@code read(byte[],int,int)}), empty string or {@code null} for type-level elements.
     */
    private String signature;

    /**
     * Chunk text content (the searchable text).
     */
    private String text;

    /**
     * Cosine similarity score from the kNN search, higher is a better match.
     */
    private float score;

    /**
     * Raw JSON representation of the element's Javadoc structure.
     */
    private String rawJson;

}
