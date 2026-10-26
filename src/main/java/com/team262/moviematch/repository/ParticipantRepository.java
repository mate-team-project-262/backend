package com.team262.moviematch.repository;

import com.team262.moviematch.model.Participant;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {
    @EntityGraph(attributePaths = "room")
    Optional<Participant> findByToken(String token);
}
