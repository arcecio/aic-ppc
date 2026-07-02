package com.lacity.aipppc.repository;

import com.lacity.aipppc.model.Document;
import com.lacity.aipppc.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    List<Document> findByProjectOrderByUploadedAtDesc(Project project);
    List<Document> findByProjectOrderByUploadedAtAsc(Project project);
    long countByProject(Project project);
}
