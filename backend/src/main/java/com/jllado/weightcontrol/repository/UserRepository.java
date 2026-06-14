package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByGoogleSub(String googleSub);
}
