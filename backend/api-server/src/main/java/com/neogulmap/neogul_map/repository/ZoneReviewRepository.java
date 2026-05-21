package com.neogulmap.neogul_map.repository;

import com.neogulmap.neogul_map.domain.ZoneReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ZoneReviewRepository extends JpaRepository<ZoneReview, Long> {

    @Query("SELECT zr FROM ZoneReview zr " +
           "JOIN FETCH zr.author " +
           "JOIN FETCH zr.zone " +
           "WHERE zr.zone.id = :zoneId " +
           "ORDER BY zr.createdAt DESC")
    List<ZoneReview> findByZoneIdWithAuthorOrderByCreatedAtDesc(@Param("zoneId") Integer zoneId);
}
