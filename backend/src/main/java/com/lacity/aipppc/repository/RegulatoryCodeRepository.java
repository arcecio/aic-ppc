package com.lacity.aipppc.repository;

import com.lacity.aipppc.model.RegulatoryCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegulatoryCodeRepository extends JpaRepository<RegulatoryCode, UUID> {
    Optional<RegulatoryCode> findByExternalId(String externalId);
    List<RegulatoryCode> findByCodeTypeOrderBySection(String codeType);

    @Query("select c from RegulatoryCode c where "
        + "lower(c.externalId) like lower(concat('%', :q, '%')) "
        + "or lower(c.title) like lower(concat('%', :q, '%')) "
        + "or lower(c.summary) like lower(concat('%', :q, '%')) "
        + "or lower(c.tags) like lower(concat('%', :q, '%')) "
        + "or lower(c.section) like lower(concat('%', :q, '%')) order by c.section")
    List<RegulatoryCode> search(@Param("q") String q);

    // ── pgvector arm (V4 migration). The embedding column is intentionally NOT
    // mapped on the JPA entity; all access is native SQL with explicit ::vector
    // casts, mirroring Blue. `<=>` is pgvector's cosine-distance operator.
    @Modifying
    @Query(value = "update regulatory_codes set embedding = cast(:vec as vector) where id = :id",
        nativeQuery = true)
    void updateEmbedding(@Param("id") UUID id, @Param("vec") String vectorLiteral);

    @Query(value = "select * from regulatory_codes where embedding is not null "
        + "order by embedding <=> cast(:vec as vector) limit :limit", nativeQuery = true)
    List<RegulatoryCode> searchByEmbedding(@Param("vec") String vectorLiteral, @Param("limit") int limit);

    @Query(value = "select * from regulatory_codes where embedding is null", nativeQuery = true)
    List<RegulatoryCode> findWithoutEmbedding();

    @Query(value = "select count(*) from regulatory_codes where embedding is not null", nativeQuery = true)
    long countEmbedded();
}
