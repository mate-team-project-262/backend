package com.team262.moviematch.repository;

// A row from the result of a grouping query: how many votes a participant cast in a given room.
public record ParticipantVoteCount(Long participantId, Long votes) {
}