package com.team262.moviematch.repository;

import com.team262.moviematch.model.Participant;
import com.team262.moviematch.model.Room;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Participant p where p.id = :id")
    Optional<Participant> findByIdForUpdate(@Param("id") Long id);

    List<Participant> findAllByRoomIdOrderByIdAsc(Long roomId);

    @EntityGraph(attributePaths = "room")
    Optional<Participant> findByToken(String token);

    long countByRoom(Room room);

    boolean existsByRoomAndNameIgnoreCase (Room room, String name);
}
