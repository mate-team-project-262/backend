package com.team262.moviematch.repository;
import com.team262.moviematch.model.User;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UserRepository extends JpaRepository<User, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findLockedById(Long id);
    Optional<User> findByEmail(String email);
    Optional<User> findByDisplayNameIgnoreCase(String displayName);
    boolean existsByDisplayNameIgnoreCase(String displayName);

    /** "Taken by someone else": the account's own row is excluded */
    boolean existsByDisplayNameIgnoreCaseAndIdNot(String displayName, Long id);

    boolean existsByEmailAndIdNot(String email, Long id);
}

