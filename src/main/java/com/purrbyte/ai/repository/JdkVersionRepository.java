package com.purrbyte.ai.repository;

import com.purrbyte.ai.domain.JdkVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JdkVersionRepository extends JpaRepository<JdkVersion, Long> {

    Optional<JdkVersion> findByVersion(String version);

    boolean existsByVersion(String version);

    /**
     * Returns all version strings with READY status, ordered from newest major to oldest.
     */
    @Query("SELECT v.version FROM JdkVersion v WHERE v.status = 'READY' ORDER BY v.major DESC, v.minor DESC, v.security DESC")
    List<String> findAllVersionStringsOrderByMajorDesc();
}
