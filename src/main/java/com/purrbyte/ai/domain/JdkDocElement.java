package com.purrbyte.ai.domain;

import com.purrbyte.ai.model.ElementKind;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@Entity
@Table(name = "jdk_doc_element")
public class JdkDocElement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jdk_version_id")
    private JdkVersion jdkVersion;

    @Enumerated(EnumType.STRING)
    private ElementKind kind;

    @Column(name = "qualified_id", nullable = false)
    private String qualifiedId; // "java.io.BufferedInputStream" | "java.io" | "module:java.base"
    private String simpleName;
    private String packageName;
    private String moduleName;

    @Lob
    @Column(name = "raw_json")
    private String rawJson; // full structural JSON file content
}
