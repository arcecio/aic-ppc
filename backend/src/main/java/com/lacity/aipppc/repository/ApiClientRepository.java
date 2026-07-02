package com.lacity.aipppc.repository;

import com.lacity.aipppc.model.ApiClient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ApiClientRepository extends JpaRepository<ApiClient, UUID> {
    Optional<ApiClient> findByKeyHashAndActiveTrue(String keyHash);
}
