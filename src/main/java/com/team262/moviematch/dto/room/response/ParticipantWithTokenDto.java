package com.team262.moviematch.dto.room.response;

public record ParticipantWithTokenDto(long id,
                                      String displayName,
                                      boolean host,
                                      String token) {
}
