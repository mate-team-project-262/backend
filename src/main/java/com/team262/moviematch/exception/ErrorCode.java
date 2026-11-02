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
    NOT_ENOUGH_TITLES,
    ROOM_NOT_VOTING,
    ALREADY_VOTED,
    RESULT_NOT_READY,

    //ExternalServiceException
    TMDB_UNAVAILABLE,
    DECK_SELECTION_LIMIT
}
