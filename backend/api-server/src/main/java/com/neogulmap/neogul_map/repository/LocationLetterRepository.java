package com.neogulmap.neogul_map.repository;

import com.neogulmap.neogul_map.domain.LocationLetter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface LocationLetterRepository extends JpaRepository<LocationLetter, Long> {

    @Query("SELECT letter FROM LocationLetter letter " +
            "JOIN FETCH letter.author " +
            "WHERE letter.latitudeBucket BETWEEN :minLatitude AND :maxLatitude " +
            "AND letter.longitudeBucket BETWEEN :minLongitude AND :maxLongitude " +
            "ORDER BY letter.createdAt DESC")
    List<LocationLetter> findNearbyCandidates(
            @Param("minLatitude") BigDecimal minLatitude,
            @Param("maxLatitude") BigDecimal maxLatitude,
            @Param("minLongitude") BigDecimal minLongitude,
            @Param("maxLongitude") BigDecimal maxLongitude,
            Pageable pageable
    );

    @Query("SELECT letter FROM LocationLetter letter " +
            "JOIN FETCH letter.author WHERE letter.id = :letterId")
    Optional<LocationLetter> findByIdWithAuthor(@Param("letterId") Long letterId);
}
