package com.neogulmap.neogul_map.repository;

import com.neogulmap.neogul_map.domain.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ZoneRepository extends JpaRepository<Zone, Integer> {
    Optional<Zone> findByAddress(String address);
}
