package com.neogulmap.neogul_map.repository.recommendation;

import com.neogulmap.neogul_map.domain.recommendation.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {

    Optional<UserPreference> findByUser_Id(Long userId);

    void deleteByUser_Id(Long userId);
}
