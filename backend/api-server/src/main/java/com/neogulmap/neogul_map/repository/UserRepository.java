package com.neogulmap.neogul_map.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import com.neogulmap.neogul_map.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

}
