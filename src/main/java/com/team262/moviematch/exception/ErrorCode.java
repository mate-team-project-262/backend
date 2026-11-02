package com.team262.moviematch.exception;

public enum ErrorCode {
    //Spring, @Valid
    VALIDATION_FAILED,

    // BadRequestException
    INVALID_PREFERENCES,
    MOVIE_NOT_IN_DECK,

    //UnauthorizedException
    INVALID_TOKEN,

    // ForbiddenException
    NOT_HOST,
    WRONG_ROOM,

    // NotFoundException
    ROOM_NOT_FOUND,

    // ConflictException
    ROOM_NOT_JOINABLE,
    ROOM_FULL,
    NAME_TAKEN,
    NOT_ENOUGH_MOVIES,
    DECK_BUILD_IN_PROGRESS,
    DECK_BUILD_STALE,
    ROOM_NOT_VOTING,
    ALREADY_VOTED,
    RESULT_NOT_READY,

    //ExternalServiceException
    TMDB_UNAVAILABLE,
    DECK_BUILD_TIMEOUT,
    DECK_SELECTION_LIMIT
}
