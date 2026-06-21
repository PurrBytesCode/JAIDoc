package com.purrbyte.ai.service;

import com.purrbyte.ai.domain.JdkDocChunk;
import com.purrbyte.ai.model.dto.JdkSearchResult;
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
public class JdkSearchService {

    private final EntityManager entityManager;
    private final EmbeddingService embeddingService;

    @Transactional(readOnly = true)
    public List<JdkSearchResult> search(String version, String query, int topK) {
        float[] queryVector = embeddingService.embedQuery(query);
        SearchSession session = Search.session(entityManager);
        return session.search(JdkDocChunk.class)
                .select(f -> f.composite()
                        .from(f.entity(), f.score())
                        .as(this::toResult))
                .where(f -> f.knn(topK)
                        .field("embedding")
                        .matching(queryVector)
                        .filter(f.match().field("version").matching(version)))  // search one version
                .fetchHits(topK);
    }

    private JdkSearchResult toResult(JdkDocChunk c, float score) {
        String rawJson = c.getJDKDocElement() != null ? c.getJDKDocElement().getRawJson() : null;
        return new JdkSearchResult(c.getChunkId(), c.getKind(), c.getQualifiedType(),
                c.getMember(), c.getSignature(), c.getText(), score, rawJson);
    }
}
