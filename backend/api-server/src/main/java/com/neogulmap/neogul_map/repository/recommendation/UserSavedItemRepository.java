package com.neogulmap.neogul_map.repository.recommendation;

import com.neogulmap.neogul_map.domain.recommendation.RecommendationItemType;
import com.neogulmap.neogul_map.domain.recommendation.UserSavedItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserSavedItemRepository extends JpaRepository<UserSavedItem, Long> {

    Optional<UserSavedItem> findByUser_IdAndItemTypeAndProviderAndItemId(
            Long userId,
            RecommendationItemType itemType,
            String provider,
            String itemId
    );

    List<UserSavedItem> findByUser_IdOrderByUpdatedAtDesc(Long userId);

    long countByUser_Id(Long userId);

    long deleteByUser_IdAndItemTypeAndProviderAndItemId(
            Long userId,
            RecommendationItemType itemType,
            String provider,
            String itemId
    );

    void deleteByUser_Id(Long userId);
}
