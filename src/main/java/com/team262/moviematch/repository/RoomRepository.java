package com.team262.moviematch.repository;

import com.team262.moviematch.model.Room;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomRepository extends JpaRepository<Room, Long> {
    Optional<Room> findByCodeIgnoreCase(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Room> findLockedById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Room> findLockedByCodeIgnoreCase(String code);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update Room r set r.deckBuildStatus = com.team262.moviematch.model.DeckBuildStatus.BUILDING
        where r.id = :id
          and r.status = com.team262.moviematch.model.RoomStatus.LOBBY
          and r.deckBuildStatus in (com.team262.moviematch.model.DeckBuildStatus.IDLE,
                                    com.team262.moviematch.model.DeckBuildStatus.FAILED)
        """)
    int claimDeckBuild(@Param("id") Long id);

    @Modifying(clearAutomatically = true)
    @Query("""
        update Room r set r.deckBuildStatus = com.team262.moviematch.model.DeckBuildStatus.FAILED
        where r.id = :id
          and r.deckBuildStatus = com.team262.moviematch.model.DeckBuildStatus.BUILDING
        """)
    int markDeckBuildFailed(@Param("id") Long id);
}
