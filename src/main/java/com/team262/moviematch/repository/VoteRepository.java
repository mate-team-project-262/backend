package com.team262.moviematch.repository;

import com.team262.moviematch.model.Vote;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoteRepository extends JpaRepository<Vote, Long> {
    Optional<Vote> findByParticipantIdAndRoomTitleId(Long participantId, Long roomTitleId);

    @EntityGraph(attributePaths = "roomTitle")
    List<Vote> findAllByParticipantIdAndRoomTitleRoomId(Long participantId, Long roomId);

    long countByParticipantIdAndRoomTitleRoomId(Long participantId, Long roomId);
}
