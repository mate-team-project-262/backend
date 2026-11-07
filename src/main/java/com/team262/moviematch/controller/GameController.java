package com.team262.moviematch.controller;

import com.team262.moviematch.config.OpenApiConfig;
import com.team262.moviematch.config.openapi.FrameworkErrorResponse;
import com.team262.moviematch.config.openapi.RoomApiExamples;
import com.team262.moviematch.dto.error.ErrorResponse;
import com.team262.moviematch.dto.game.GameProgressResponse;
import com.team262.moviematch.dto.game.GameResultsResponse;
import com.team262.moviematch.dto.room.response.StartGameResponse;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.security.CurrentParticipant;
import com.team262.moviematch.service.game.GameProgressService;
import com.team262.moviematch.service.game.GameResultService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
@Tag(name = "Game", description = "Game start, voting progress and results")
public class GameController {
    private final GameStartService gameStartService;
    private final GameProgressService gameProgressService;
    private final GameResultService gameResultService;

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
                            schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "Missing token", value = RoomApiExamples.MISSING_TOKEN),
                            @ExampleObject(name = "Unknown token", value = RoomApiExamples.INVALID_TOKEN),
                            @ExampleObject(name = "Expired token", value = RoomApiExamples.EXPIRED_TOKEN)})),
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
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = RoomApiExamples.ROOM_NOT_FOUND))),
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

    @GetMapping("/{code}/progress")
    @Operation(operationId = "getGameProgress", summary = "Get voting progress for the waiting screen",
            description = "Requires X-Participant-Token of a participant in this room; there is no request body. "
                    + "A participant is finished when they have a saved vote (like or skip) on every card of "
                    + "the room's deck. Poll about every 3 seconds. Show finishedParticipants / totalParticipants "
                    + "as '3 / 4 completed' and participants[].finished as Done / Swiping... "
                    + "When allFinished is true, read the final cards at GET /rooms/{code}/results. "
                    + "Checks run in order: token, room existence, room membership, status. "
                    + "Tokens are never returned.",
            security = @SecurityRequirement(name = OpenApiConfig.PARTICIPANT_TOKEN), responses = {
            @ApiResponse(responseCode = "200", description = "Current progress (room status VOTING or FINISHED)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = GameProgressResponse.class),
                            examples = @ExampleObject(value = "{\"finishedParticipants\":3,\"totalParticipants\":4,"
                                    + "\"myFinished\":true,\"allFinished\":false,\"participants\":["
                                    + "{\"id\":1,\"displayName\":\"Sofia\",\"host\":true,\"finished\":true},"
                                    + "{\"id\":2,\"displayName\":\"Anna\",\"host\":false,\"finished\":true},"
                                    + "{\"id\":3,\"displayName\":\"Peter\",\"host\":false,\"finished\":true},"
                                    + "{\"id\":4,\"displayName\":\"Alex\",\"host\":false,\"finished\":false}]}"))),
            @ApiResponse(responseCode = "401", description = "INVALID_TOKEN: missing, blank, unknown or expired token",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "Missing token", value = RoomApiExamples.MISSING_TOKEN),
                            @ExampleObject(name = "Unknown token", value = RoomApiExamples.INVALID_TOKEN),
                            @ExampleObject(name = "Expired token", value = RoomApiExamples.EXPIRED_TOKEN)})),
            @ApiResponse(responseCode = "403", description = "WRONG_ROOM: the token belongs to another room",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = RoomApiExamples.WRONG_ROOM))),
            @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = RoomApiExamples.ROOM_NOT_FOUND))),
            @ApiResponse(responseCode = "500", description = "Unexpected server failure",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FrameworkErrorResponse.class),
                            examples = @ExampleObject(value =
                                    "{\"status\":500,\"error\":\"Internal Server Error\",\"path\":\"/rooms/K7M2QX/progress\"}")))
    })
    public GameProgressResponse getProgress(
            @Parameter(description = "Case-insensitive room code", example = "K7M2QX", required = true)
            @PathVariable String code,
            @CurrentParticipant Participant participant) {
        return gameProgressService.getProgress(code, participant);
    }

    @GetMapping("/{code}/results")
    @Operation(operationId = "getGameResults", summary = "Get the full results of a finished round",
            description = "Returns all cards, sorted by likes descending and then by deck position. "
                    + "All cards tied for the most likes are marked winner if at least one like was cast. "
                    + "The counts are fixed at the end of the round.",
            security = @SecurityRequirement(name = OpenApiConfig.PARTICIPANT_TOKEN))
    @ApiResponse(responseCode = "200", description = "Final results",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = GameResultsResponse.class)))
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "WRONG_ROOM",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "RESULT_NOT_READY",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    public GameResultsResponse getResults(@PathVariable String code,
            @Parameter(hidden = true) @CurrentParticipant Participant participant) {
        return gameResultService.getResults(code, participant);
    }
}
