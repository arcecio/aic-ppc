package com.lacity.aipppc.repository;

import com.lacity.aipppc.model.RegulatoryCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegulatoryCodeRepository extends JpaRepository<RegulatoryCode, UUID> {
    Optional<RegulatoryCode> findByExternalId(String externalId);
    List<RegulatoryCode> findByCodeTypeOrderBySection(String codeType);

    @Query("select c from RegulatoryCode c where "
        + "lower(c.title) like lower(concat('%', :q, '%')) "
        + "or lower(c.summary) like lower(concat('%', :q, '%')) "
        + "or lower(c.tags) like lower(concat('%', :q, '%')) "
        + "or lower(c.section) like lower(concat('%', :q, '%')) order by c.section")
    List<RegulatoryCode> search(@Param("q") String q);
}
