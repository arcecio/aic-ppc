package com.lacity.aipppc.repository;

import com.lacity.aipppc.model.Parcel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParcelRepository extends JpaRepository<Parcel, UUID> {
    Optional<Parcel> findByApn(String apn);
    Optional<Parcel> findByAddressNormalized(String addressNormalized);

    @Query("select p from Parcel p where lower(p.address) like lower(concat('%', :q, '%')) "
        + "or p.apn like concat('%', :q, '%') order by p.address")
    List<Parcel> search(@Param("q") String q);
}
