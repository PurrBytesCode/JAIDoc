package com.purrbyte.ai.domain;

import com.purrbyte.ai.model.IngestStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@Entity
@Table(name = "jdk_version", uniqueConstraints = @UniqueConstraint(columnNames = "version"))
public class JdkVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String version; // "25.0.3"

    private int major;
    private int minor;
    private int security;

    private String javaRuntime; // index.json "javaRuntime"
    private String generator; // index.json "generator"
    private Instant generatedAt; // index.json "generatedAt"

    private int typeCount;
    private int packageCount;
    private int moduleCount;
    private int chunkCount;

    private Instant ingestedAt;

    @Enumerated(EnumType.STRING)
    private IngestStatus status = IngestStatus.INGESTING;

    @OneToMany(mappedBy = "jdkVersion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JdkDocElement> elements = new ArrayList<>();

    @OneToMany(mappedBy = "jdkVersion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JdkDocChunk> chunks = new ArrayList<>();
}
