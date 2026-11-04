package com.team262.moviematch.controller;

import com.team262.moviematch.config.OpenApiConfig;
import com.team262.moviematch.config.openapi.FrameworkErrorResponse;
import com.team262.moviematch.dto.error.ErrorResponse;
import com.team262.moviematch.dto.room.response.StartGameResponse;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.security.CurrentParticipant;
import com.team262.moviematch.service.game.GameStartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
@Tag(name = "Game", description = "Starting the game and swiping")
public class GameController {
    private final GameStartService gameStartService;

    @PostMapping("/{code}/start")
    @Operation(operationId = "startGame", summary = "Start the game (host only)",
            description = "Requires the host's X-Participant-Token; there is no request body. "
                    + "Builds a deck of 20 titles from the room preferences, saves it and moves the room "
                    + "from LOBBY to VOTING. The call contacts TMDB and may take several seconds, so show "
                    + "a loader and disable the Start button. Checks run in order: token, room existence, "
                    + "room membership, host role, status LOBBY, at least 2 participants. "
                    + "On any failure the room stays in LOBBY and the host may retry.",
            security = @SecurityRequirement(name = OpenApiConfig.PARTICIPANT_TOKEN), responses = {
                @ApiResponse(responseCode = "200", description = "Game started, deck saved",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = StartGameResponse.class),
                                examples = @ExampleObject(
                                        value = "{\"code\":\"K7M2QX\",\"status\":\"VOTING\",\"deckSize\":20}"))),
                @ApiResponse(responseCode = "401", description = "INVALID_TOKEN: missing, blank, unknown or expired token",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class))),
                @ApiResponse(responseCode = "403", description = "WRONG_ROOM (token of another room) "
                        + "or NOT_HOST (only the host can start the game)",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class), examples = {
                                    @ExampleObject(name = "Not host",
                                            value = "{\"code\":\"NOT_HOST\",\"message\":\"Only the host can start the game\",\"fieldErrors\":[]}"),
                                    @ExampleObject(name = "Wrong room",
                                            value = "{\"code\":\"WRONG_ROOM\",\"message\":\"Participant does not belong to this room\",\"fieldErrors\":[]}")})),
                @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class))),
                @ApiResponse(responseCode = "409", description = "ROOM_ALREADY_STARTED (status is not LOBBY "
                        + "or a start is in progress), NOT_ENOUGH_PARTICIPANTS (fewer than 2), "
                        + "NOT_ENOUGH_TITLES (fewer than 20 titles match the preferences) "
                        + "or DECK_SELECTION_LIMIT (the deck could not be filled within the request budget). "
                        + "The last two: change the preferences or retry.",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class), examples = {
                                    @ExampleObject(name = "Already started",
                                            value = "{\"code\":\"ROOM_ALREADY_STARTED\",\"message\":\"Game has already started or is being started\",\"fieldErrors\":[]}"),
                                    @ExampleObject(name = "Not enough participants",
                                            value = "{\"code\":\"NOT_ENOUGH_PARTICIPANTS\",\"message\":\"At least 2 participants are required\",\"fieldErrors\":[]}"),
                                    @ExampleObject(name = "Not enough titles",
                                            value = "{\"code\":\"NOT_ENOUGH_TITLES\",\"message\":\"Fewer than 20 suitable titles match the filters\",\"fieldErrors\":[]}")})),
                @ApiResponse(responseCode = "502", description = "TMDB_UNAVAILABLE: TMDB timed out or returned an error. "
                        + "The room stays in LOBBY; the host may retry.",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples = @ExampleObject(
                                        value = "{\"code\":\"TMDB_UNAVAILABLE\",\"message\":\"TMDB is unavailable\",\"fieldErrors\":[]}"))),
                @ApiResponse(responseCode = "500", description = "Unexpected server failure",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = FrameworkErrorResponse.class)))
            })
    public StartGameResponse startGame(
            @Parameter(description = "Case-insensitive room code", example = "K7M2QX", required = true)
            @PathVariable String code,
            @CurrentParticipant Participant participant) {
        return gameStartService.start(code, participant);
    }
}