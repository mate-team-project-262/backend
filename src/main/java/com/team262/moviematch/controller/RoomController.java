package com.team262.moviematch.controller;

import com.team262.moviematch.dto.room.request.CreateRoomRequest;
import com.team262.moviematch.dto.room.request.JoinRoomRequest;
import com.team262.moviematch.dto.room.response.CreateRoomResponse;
import com.team262.moviematch.dto.room.response.JoinRoomResponse;
import com.team262.moviematch.dto.room.response.RoomDetailsResponse;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.security.CurrentParticipant;
import com.team262.moviematch.service.RoomService;
import com.team262.moviematch.config.OpenApiConfig;
import com.team262.moviematch.config.openapi.FrameworkErrorResponse;
import com.team262.moviematch.config.openapi.RoomApiExamples;
import com.team262.moviematch.dto.error.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
@Tag(name = "Rooms", description = "Room creation, joining and lobby retrieval")
public class RoomController {
    private final RoomService roomService;

    @PostMapping
    @Operation(operationId = "createRoom", summary = "Create a room and become its host",
            description = "No token is required. The room is created in LOBBY status. "
                    + "Omit preferences or send null to use MOVIES, an empty genre set "
                    + "and no duration limit. Save participant.token for GET lobby. "
                    + "The frontend builds the invitation link from code.",
            responses = {
                @ApiResponse(responseCode = "201", description = "Room and host created",
                        headers = @Header(name = "Location", description = "Lobby URL; GET requires a participant token",
                                schema = @Schema(type = "string", example = "/rooms/K7M2QX")),
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = CreateRoomResponse.class), examples = {
                                    @ExampleObject(name = "With preferences", value = RoomApiExamples.CREATE_RESPONSE),
                                    @ExampleObject(name = "Default preferences", value = RoomApiExamples.DEFAULT_RESPONSE)})),
                @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: invalid display name or preferences. "
                        + "Malformed JSON, an unknown contentType, a missing body or an invalid field type "
                        + "also return 400, using Spring's default format without code.",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(oneOf = {ErrorResponse.class, FrameworkErrorResponse.class}), examples = {
                                    @ExampleObject(name = "Validation error", value = RoomApiExamples.VALIDATION_ERROR),
                                    @ExampleObject(name = "JSON error", value = RoomApiExamples.BAD_JSON)})),
                @ApiResponse(responseCode = "415", description = "Unsupported Content-Type; send application/json",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = FrameworkErrorResponse.class))),
                @ApiResponse(responseCode = "503", description = "Failed to allocate a unique room code after 5 attempts. "
                        + "Creation can be retried; no business error code is provided.",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = FrameworkErrorResponse.class),
                                examples = @ExampleObject(value = "{\"status\":503,\"error\":\"Service Unavailable\",\"path\":\"/rooms\"}"))),
                @ApiResponse(responseCode = "500", description = "Unexpected server failure",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = FrameworkErrorResponse.class)))
            })
    public ResponseEntity<CreateRoomResponse> createRoom(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CreateRoomRequest.class), examples = {
                                @ExampleObject(name = "With preferences", value = RoomApiExamples.CREATE_REQUEST),
                                @ExampleObject(name = "Default preferences", value = RoomApiExamples.DEFAULT_REQUEST)}))
            @Valid @RequestBody CreateRoomRequest request) {
        CreateRoomResponse response = roomService.createRoom(request);
        return ResponseEntity.created(URI.create("/rooms/" + response.code())).body(response);
    }

    @GetMapping("/{code}")
    @Operation(operationId = "getRoomLobby", summary = "Get a room and its participants",
            description = "Requires a valid X-Participant-Token belonging to a participant in this room. "
                    + "There is no request body: the server resolves the participant from the token. "
                    + "Returns the current status (including VOTING/FINISHED), preferences and participants "
                    + "ordered by ID, without tokens. Checks run in order: token validity, room existence "
                    + "and room membership. Public access before creating or joining a room is not supported.",
            security = @SecurityRequirement(name = OpenApiConfig.PARTICIPANT_TOKEN), responses = {
                @ApiResponse(responseCode = "200", description = "Current room state",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = RoomDetailsResponse.class),
                                examples = @ExampleObject(value = RoomApiExamples.LOBBY_RESPONSE))),
                @ApiResponse(responseCode = "401", description = "INVALID_TOKEN: the token is missing, blank, unknown "
                        + "or older than 24 hours. Obtain a valid token by creating or joining a room.",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class), examples = {
                                    @ExampleObject(name = "Missing token", value = RoomApiExamples.MISSING_TOKEN),
                                    @ExampleObject(name = "Unknown token", value = RoomApiExamples.INVALID_TOKEN),
                                    @ExampleObject(name = "Expired token", value = RoomApiExamples.EXPIRED_TOKEN)})),
                @ApiResponse(responseCode = "403", description = "WRONG_ROOM: the token is valid but belongs to another room",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples = @ExampleObject(value = RoomApiExamples.WRONG_ROOM))),
                @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND: the token is valid but the room does not exist",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples = @ExampleObject(value = RoomApiExamples.ROOM_NOT_FOUND))),
                @ApiResponse(responseCode = "500", description = "Unexpected server failure",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = FrameworkErrorResponse.class)))
            })
    public RoomDetailsResponse getLobby(
            @Parameter(description = "Case-insensitive room code", example = "K7M2QX", required = true)
            @PathVariable String code,
                                        @CurrentParticipant Participant participant) {
        return roomService.getLobby(code, participant);
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/{code}/join")
    @Operation(operationId = "joinRoom", summary = "Join a room by code",
            description = "No token is required. The room must be in LOBBY status; the current limit is 5 participants "
                    + "including the host. The display name must be available in this room (case-insensitive). "
                    + "Success creates a new participant with host=false. Save currentParticipant.token for GET. "
                    + "Repeating a request with the same name does not restore a session; it returns NAME_TAKEN.",
            responses = {
                @ApiResponse(responseCode = "201", description = "Participant added to the room",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = JoinRoomResponse.class),
                                examples = @ExampleObject(value = RoomApiExamples.JOIN_RESPONSE))),
                @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: the display name is blank or not 3-20 characters long. "
                        + "Malformed JSON, an invalid field type or a missing body return Spring's default error without code.",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(oneOf = {ErrorResponse.class, FrameworkErrorResponse.class}), examples = {
                                    @ExampleObject(name = "Validation error", value = RoomApiExamples.VALIDATION_ERROR)})),
                @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND: the room does not exist",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples = @ExampleObject(value = RoomApiExamples.ROOM_NOT_FOUND))),
                @ApiResponse(responseCode = "409", description = "Conflict. Checks run in order: ROOM_NOT_JOINABLE "
                        + "(status is not LOBBY), ROOM_FULL (already 5 participants), NAME_TAKEN (name taken, case-insensitive).",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class), examples = {
                                    @ExampleObject(name = "Room full", value = RoomApiExamples.ROOM_FULL),
                                    @ExampleObject(name = "Name taken", value = RoomApiExamples.NAME_TAKEN),
                                    @ExampleObject(name = "Room not joinable", value = RoomApiExamples.ROOM_NOT_JOINABLE)})),
                @ApiResponse(responseCode = "415", description = "Unsupported Content-Type; send application/json",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = FrameworkErrorResponse.class))),
                @ApiResponse(responseCode = "500", description = "Technical failure, including exhaustion of 5 attempts "
                        + "to generate a unique participant token. No business error code is provided.",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = FrameworkErrorResponse.class)))
            })
    public JoinRoomResponse joinRoom(
            @Parameter(description = "Case-insensitive room code from the invitation", example = "K7M2QX", required = true)
            @PathVariable String code,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = JoinRoomRequest.class),
                            examples = @ExampleObject(value = RoomApiExamples.JOIN_REQUEST)))
            @Valid @RequestBody JoinRoomRequest request) {
        return roomService.joinRoom(code, request);
    }
}
