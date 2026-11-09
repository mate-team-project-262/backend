package com.team262.moviematch.repository;
import com.team262.moviematch.model.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByDisplayNameIgnoreCase(String displayName);
    boolean existsByDisplayNameIgnoreCase(String displayName);
}

