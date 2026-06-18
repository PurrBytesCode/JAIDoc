package com.purrbyte.ai.repository;

import com.purrbyte.ai.domain.JdkVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JdkVersionRepository extends JpaRepository<JdkVersion, Long> {

    Optional<JdkVersion> findByVersion(String version);

    boolean existsByVersion(String version);
}
