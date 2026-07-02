package com.lacity.aipppc.repository;

import com.lacity.aipppc.model.PermitType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PermitTypeRepository extends JpaRepository<PermitType, UUID> {
    Optional<PermitType> findByCode(String code);
    boolean existsByCode(String code);
    List<PermitType> findByActiveTrueOrderByName();
}
