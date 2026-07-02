package com.lacity.aipppc.repository;

import com.lacity.aipppc.model.FeedbackEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FeedbackEntryRepository extends JpaRepository<FeedbackEntry, UUID> {
    List<FeedbackEntry> findAllByOrderByCreatedAtDesc();
    List<FeedbackEntry> findByStatusOrderByCreatedAtDesc(String status);
    long countByStatus(String status);
}
