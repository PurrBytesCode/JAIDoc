package com.purrbyte.ai.repository;

import com.purrbyte.ai.domain.JdkDocElement;
import com.purrbyte.ai.domain.JdkVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JdkDocElementRepository extends JpaRepository<JdkDocElement, Long> {

    Optional<JdkDocElement> findByJdkVersionAndQualifiedId(JdkVersion jdkVersion, String qualifiedId);
}
