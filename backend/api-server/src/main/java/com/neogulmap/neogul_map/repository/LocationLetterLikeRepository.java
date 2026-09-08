package com.neogulmap.neogul_map.repository;

import com.neogulmap.neogul_map.domain.LocationLetterLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LocationLetterLikeRepository extends JpaRepository<LocationLetterLike, Long> {

    boolean existsByLetterIdAndUserId(Long letterId, Long userId);

    long countByLetterId(Long letterId);

    long deleteByLetterIdAndUserId(Long letterId, Long userId);

    @Query("SELECT reaction.letter.id, COUNT(reaction.id) " +
            "FROM LocationLetterLike reaction " +
            "WHERE reaction.letter.id IN :letterIds " +
            "GROUP BY reaction.letter.id")
    List<Object[]> countByLetterIds(@Param("letterIds") List<Long> letterIds);

    @Query("SELECT reaction.letter.id FROM LocationLetterLike reaction " +
            "WHERE reaction.user.id = :userId AND reaction.letter.id IN :letterIds")
    List<Long> findLikedLetterIds(
            @Param("userId") Long userId,
            @Param("letterIds") List<Long> letterIds
    );
}
