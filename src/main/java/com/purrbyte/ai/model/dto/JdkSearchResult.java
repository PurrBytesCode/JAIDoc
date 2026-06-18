package com.purrbyte.ai.model.dto;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
public final class JdkSearchResult {

    private String chunkId;
    private String kind;
    private String qualifiedType;
    private String member;
    private String signature;
    private String text;
    private float score;
    private String rawJson;

}
