package com.purrbyte.ai.domain;

import com.purrbyte.ai.model.converter.FloatArrayConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.search.engine.backend.types.VectorSimilarity;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.*;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@Entity
@Indexed
@Table(name = "jdk_doc_chunk", uniqueConstraints = @UniqueConstraint(columnNames = {"jdk_version_id", "chunk_id"}))
public class JdkDocChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jdk_version_id")
    private JdkVersion jdkVersion;

    // Denormalized: this is the field the kNN query filters on (search one version at a time).
    @KeywordField
    @Column(nullable = false)
    private String version;

    @Column(name = "chunk_id", nullable = false)
    private String chunkId; // "java.io.BufferedInputStream#read(byte[],int,int)"

    @FullTextField
    @Lob
    private String text;

    @VectorField(dimension = 384, vectorSimilarity = VectorSimilarity.COSINE)
    @Convert(converter = FloatArrayConverter.class)
    private float[] embedding;

    @KeywordField
    private String kind;
    @KeywordField
    private String qualifiedType;
    @KeywordField
    private String packageName;
    @KeywordField
    private String moduleName;
    @KeywordField
    private String member;
    @KeywordField
    private String signature;
    @KeywordField
    private String since;
    @GenericField
    private boolean deprecated;

    private String sourceFile;
    private int sourceLine;
    private int part;
    private int parts;
    private String parentChunkId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doc_element_id")
    private JdkDocElement JDKDocElement;
}
