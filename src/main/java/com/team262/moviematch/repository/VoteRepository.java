package com.team262.moviematch.repository;

import com.team262.moviematch.model.Vote;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoteRepository extends JpaRepository<Vote, Long> {
    Optional<Vote> findByParticipantIdAndRoomTitleId(Long participantId, Long roomTitleId);

    @EntityGraph(attributePaths = "roomTitle")
    List<Vote> findAllByParticipantIdAndRoomTitleRoomId(Long participantId, Long roomId);

    long countByParticipantIdAndRoomTitleRoomId(Long participantId, Long roomId);

    /*
    A participant with no votes does not appear in the results.
    The absence of a row signifies zero, and the service treats it as such.
    */
    @Query("""
        select new com.team262.moviematch.repository.ParticipantVoteCount(v.participant.id, count(v))
        from Vote v
        where v.roomTitle.room.id = :roomId
        group by v.participant.id
        """)
    List<ParticipantVoteCount> countVotesByParticipantInRoom(@Param("roomId") Long roomId);

    @Query("""
        select new com.team262.moviematch.repository.CardLikeCount(v.roomTitle.id, count(v))
        from Vote v
        where v.roomTitle.room.id = :roomId and v.liked = true
        group by v.roomTitle.id
        """)
    List<CardLikeCount> countLikesByCardInRoom(@Param("roomId") Long roomId);
}
