package com.purrbyte.ai.model;

import lombok.*;
import tools.jackson.databind.JsonNode;

/**
 * Lightweight holder for parsed chunk data before and after embedding generation.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
public final class ChunkData {

    private String chunkId;
    private String text;
    private String ownerId;
    private String kind;
    private String qualifiedType;
    private String packageName;
    private String moduleName;
    private String member;
    private String signature;
    private String since;
    private boolean deprecated;
    private String sourceFile;
    private int sourceLine;
    private int part;
    private int parts;
    private String parentChunkId;
    private float[] embedding;

    public static ChunkData from(JsonNode node) {
        JsonNode meta = node.path("metadata");
        String ownerId = str(meta, "type");
        String chunkId = node.path("id").asString(null);
        String text = node.path("text").asString(null);
        if (ownerId == null) {
            ownerId = chunkId;
        }
        return ChunkData.builder()
                .chunkId(chunkId)
                .text(text)
                .ownerId(ownerId)
                .kind(str(meta, "kind"))
                .qualifiedType(str(meta, "type"))
                .packageName(str(meta, "package"))
                .moduleName(str(meta, "module"))
                .member(str(meta, "member"))
                .signature(str(meta, "signature"))
                .since(str(meta, "since"))
                .deprecated(meta.path("deprecated").asBoolean())
                .sourceFile(str(meta, "file"))
                .sourceLine(meta.path("line").asInt())
                .part(meta.path("part").asInt())
                .parts(meta.path("parts").asInt())
                .parentChunkId(str(meta, "parentId"))
                .build();
    }

    /**
     * Returns the field's text, or {@code null} when absent/null (Jackson 3 tree access).
     */
    private static String str(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asString(null);
    }
}
