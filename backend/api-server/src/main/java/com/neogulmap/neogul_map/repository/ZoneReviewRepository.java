package com.neogulmap.neogul_map.repository;

import com.neogulmap.neogul_map.domain.ZoneReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ZoneReviewRepository extends JpaRepository<ZoneReview, Long> {

    @Query("SELECT zr FROM ZoneReview zr " +
           "LEFT JOIN FETCH zr.author " +
           "JOIN FETCH zr.zone " +
           "WHERE zr.zone.id = :zoneId " +
           "ORDER BY zr.createdAt DESC")
    List<ZoneReview> findByZoneIdWithAuthorOrderByCreatedAtDesc(@Param("zoneId") Integer zoneId);

    @Query("SELECT zr FROM ZoneReview zr " +
           "LEFT JOIN FETCH zr.author " +
           "JOIN FETCH zr.zone " +
           "WHERE zr.id = :reviewId")
    Optional<ZoneReview> findByIdWithAuthorAndZone(@Param("reviewId") Long reviewId);

    void deleteByAuthorId(Long authorId);
}
