package com.purrbyte.ai.service;

import com.purrbyte.ai.domain.DocChunk;
import com.purrbyte.ai.model.dto.SearchResult;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class JavadocSearchService {

    private final EntityManager entityManager;
    private final EmbeddingService embeddingService;

    @Transactional(readOnly = true)
    public List<SearchResult> search(String version, String query, int topK) {
        float[] queryVector = embeddingService.embedQuery(query);
        SearchSession session = Search.session(entityManager);
        return session.search(DocChunk.class)
                .select(f -> f.composite()
                        .from(f.entity(), f.score())
                        .as((chunk, score) -> toResult((DocChunk) chunk, score)))
                .where(f -> f.knn(topK)
                        .field("embedding")
                        .matching(queryVector)
                        .filter(f.match().field("version").matching(version)))  // search one version
                .fetchHits(topK);
    }

    private SearchResult toResult(DocChunk c, float score) {
        String rawJson = c.getDocElement() != null ? c.getDocElement().getRawJson() : null;
        return new SearchResult(c.getChunkId(), c.getKind(), c.getQualifiedType(),
                c.getMember(), c.getSignature(), c.getText(), score, rawJson);
    }
}
