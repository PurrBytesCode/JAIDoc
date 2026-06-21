package com.purrbyte.ai.domain;

import com.purrbyte.ai.model.IngestStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@Entity
@Table(name = "jdk_version")
public class JdkVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String version; // "25.0.3"

    private int major;
    private int minor;
    private int security;

    private String javaRuntime; // index.json "javaRuntime"
    private String generator; // index.json "generator"
    private LocalDateTime generatedAt; // index.json "generatedAt"

    private int typeCount;
    private int packageCount;
    private int moduleCount;
    private int chunkCount;

    private LocalDateTime ingestedAt;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    private IngestStatus status = IngestStatus.INGESTING;

    @Builder.Default
    @OneToMany(mappedBy = "jdkVersion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JdkDocElement> elements = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "jdkVersion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JdkDocChunk> chunks = new ArrayList<>();
}
