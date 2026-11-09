package com.team262.moviematch.repository;

import com.team262.moviematch.model.history.GameSessionArchive;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameSessionArchiveRepository extends JpaRepository<GameSessionArchive, Long> {
    Page<GameSessionArchive> findDistinctByParticipants_UserId(Long userId, Pageable pageable);

    Optional<GameSessionArchive> findDistinctByIdAndParticipants_UserId(Long id, Long userId);

    boolean existsBySourceRoomIdAndParticipants_UserId(Long roomId, Long userId);

    Optional<GameSessionArchive> findBySourceRoomId(Long sourceRoomId);

    boolean existsBySourceRoomId(Long sourceRoomId);
}

