package com.team262.moviematch.repository;

import com.team262.moviematch.model.Participant;
import com.team262.moviematch.model.Room;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {
    List<Participant> findAllByRoomIdOrderByIdAsc(Long roomId);

    @EntityGraph(attributePaths = "room")
    Optional<Participant> findByToken(String token);

    long countByRoom(Room room);

    boolean existsByRoomAndNameIgnoreCase (Room room, String name);
}
