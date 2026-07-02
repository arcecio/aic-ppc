package com.lacity.aipppc.repository;

import com.lacity.aipppc.model.Project;
import com.lacity.aipppc.model.User;
import com.lacity.aipppc.model.enums.ReadinessStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    List<Project> findByOwnerOrderByCreatedAtDesc(User owner);
    Optional<Project> findByIdAndOwner(UUID id, User owner);
    Optional<Project> findByUniversalProjectId(String universalProjectId);
    boolean existsByUniversalProjectId(String universalProjectId);

    long countByUsedAipPpcTrue();
    long countByCurrentReadinessStatus(ReadinessStatus status);
    long countBySubmittedToEplanlaAtNotNull();
}
