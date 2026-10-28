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
@Tag(name = "Rooms", description = "Создание комнаты, присоединение и получение лобби")
public class RoomController {
    private final RoomService roomService;

    @PostMapping
    @Operation(operationId = "createRoom", summary = "Создать комнату и стать хостом",
            description = "Токен не требуется. Комната создаётся в статусе LOBBY. "
                    + "preferences можно не передавать или передать null: используются MOVIES, пустые жанры "
                    + "и отсутствие ограничения длительности. Сохраните participant.token для GET лобби. "
                    + "Ссылку приглашения фронтенд формирует из code самостоятельно.",
            responses = {
                @ApiResponse(responseCode = "201", description = "Комната и хост созданы",
                        headers = @Header(name = "Location", description = "Адрес лобби; для GET потребуется токен",
                                schema = @Schema(type = "string", example = "/rooms/K7M2QX")),
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = CreateRoomResponse.class), examples = {
                                    @ExampleObject(name = "С предпочтениями", value = RoomApiExamples.CREATE_RESPONSE),
                                    @ExampleObject(name = "По умолчанию", value = RoomApiExamples.DEFAULT_RESPONSE)})),
                @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: неверное имя или настройки. "
                        + "Некорректный JSON, неизвестный contentType, отсутствующее тело или неверный тип поля "
                        + "также дают 400, но в стандартном формате Spring без code.",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(oneOf = {ErrorResponse.class, FrameworkErrorResponse.class}), examples = {
                                    @ExampleObject(name = "Валидация", value = RoomApiExamples.VALIDATION_ERROR),
                                    @ExampleObject(name = "Ошибка JSON", value = RoomApiExamples.BAD_JSON)})),
                @ApiResponse(responseCode = "415", description = "Неподдерживаемый Content-Type; отправляйте application/json",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = FrameworkErrorResponse.class))),
                @ApiResponse(responseCode = "503", description = "Не удалось выделить уникальный код за 5 попыток. "
                        + "Можно повторить создание; поле code ошибки не предусмотрено.",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = FrameworkErrorResponse.class),
                                examples = @ExampleObject(value = "{\"status\":503,\"error\":\"Service Unavailable\",\"path\":\"/rooms\"}"))),
                @ApiResponse(responseCode = "500", description = "Неожиданная техническая ошибка сервера",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = FrameworkErrorResponse.class)))
            })
    public ResponseEntity<CreateRoomResponse> createRoom(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CreateRoomRequest.class), examples = {
                                @ExampleObject(name = "С предпочтениями", value = RoomApiExamples.CREATE_REQUEST),
                                @ExampleObject(name = "По умолчанию", value = RoomApiExamples.DEFAULT_REQUEST)}))
            @Valid @RequestBody CreateRoomRequest request) {
        CreateRoomResponse response = roomService.createRoom(request);
        return ResponseEntity.created(URI.create("/rooms/" + response.code())).body(response);
    }

    @GetMapping("/{code}")
    @Operation(operationId = "getRoomLobby", summary = "Получить комнату и список участников",
            description = "Доступно только участнику этой комнаты с действующим токеном X-Participant-Token. "
                    + "Тела запроса нет: участник определяется сервером по токену. "
                    + "Возвращает текущий статус (в том числе VOTING/FINISHED), настройки и список участников "
                    + "по возрастанию ID, без токенов. Сначала проверяется токен, затем существование комнаты "
                    + "и принадлежность участника. До создания/присоединения публичного просмотра нет.",
            security = @SecurityRequirement(name = OpenApiConfig.PARTICIPANT_TOKEN), responses = {
                @ApiResponse(responseCode = "200", description = "Текущее состояние комнаты",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = RoomDetailsResponse.class),
                                examples = @ExampleObject(value = RoomApiExamples.LOBBY_RESPONSE))),
                @ApiResponse(responseCode = "401", description = "INVALID_TOKEN: токен отсутствует, пустой, неизвестен "
                        + "или старше 24 часов. Нужно получить действующий токен при создании/присоединении.",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class), examples = {
                                    @ExampleObject(name = "Нет токена", value = RoomApiExamples.MISSING_TOKEN),
                                    @ExampleObject(name = "Неизвестный токен", value = RoomApiExamples.INVALID_TOKEN),
                                    @ExampleObject(name = "Истёкший токен", value = RoomApiExamples.EXPIRED_TOKEN)})),
                @ApiResponse(responseCode = "403", description = "WRONG_ROOM: токен действителен, но участник из другой комнаты",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples = @ExampleObject(value = RoomApiExamples.WRONG_ROOM))),
                @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND: при действующем токене комната не найдена",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples = @ExampleObject(value = RoomApiExamples.ROOM_NOT_FOUND))),
                @ApiResponse(responseCode = "500", description = "Неожиданная техническая ошибка сервера",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = FrameworkErrorResponse.class)))
            })
    public RoomDetailsResponse getLobby(
            @Parameter(description = "Код комнаты, регистр не важен", example = "K7M2QX", required = true)
            @PathVariable String code,
                                        @CurrentParticipant Participant participant) {
        return roomService.getLobby(code, participant);
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/{code}/join")
    @Operation(operationId = "joinRoom", summary = "Присоединиться к комнате по коду",
            description = "Токен не требуется. Комната должна быть в статусе LOBBY; текущий лимит — 5 человек "
                    + "вместе с хостом. Имя должно быть свободно в комнате без учёта регистра. "
                    + "При успехе создаётся новый участник с host=false. Сохраните currentParticipant.token для GET. "
                    + "Повтор запроса с тем же именем не восстанавливает сессию, а возвращает NAME_TAKEN.",
            responses = {
                @ApiResponse(responseCode = "201", description = "Участник добавлен в комнату",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = JoinRoomResponse.class),
                                examples = @ExampleObject(value = RoomApiExamples.JOIN_RESPONSE))),
                @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: имя пустое или не длиной 3–20 символов. "
                        + "Ошибка JSON/типа поля или отсутствующее тело дают стандартную ошибку Spring без code.",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(oneOf = {ErrorResponse.class, FrameworkErrorResponse.class}), examples = {
                                    @ExampleObject(name = "Валидация", value = RoomApiExamples.VALIDATION_ERROR)})),
                @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND: комната не найдена",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples = @ExampleObject(value = RoomApiExamples.ROOM_NOT_FOUND))),
                @ApiResponse(responseCode = "409", description = "Конфликт. Проверки идут по порядку: ROOM_NOT_JOINABLE "
                        + "(статус не LOBBY), ROOM_FULL (уже 5 участников), NAME_TAKEN (имя занято без учёта регистра).",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class), examples = {
                                    @ExampleObject(name = "Комната заполнена", value = RoomApiExamples.ROOM_FULL),
                                    @ExampleObject(name = "Имя занято", value = RoomApiExamples.NAME_TAKEN),
                                    @ExampleObject(name = "Вход закрыт", value = RoomApiExamples.ROOM_NOT_JOINABLE)})),
                @ApiResponse(responseCode = "415", description = "Неподдерживаемый Content-Type; отправляйте application/json",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = FrameworkErrorResponse.class))),
                @ApiResponse(responseCode = "500", description = "Техническая ошибка, в том числе исчерпание 5 попыток "
                        + "генерации токена. Бизнес-код ошибки не предусмотрен.",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = FrameworkErrorResponse.class)))
            })
    public JoinRoomResponse joinRoom(
            @Parameter(description = "Код комнаты из приглашения, регистр не важен", example = "K7M2QX", required = true)
            @PathVariable String code,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = JoinRoomRequest.class),
                            examples = @ExampleObject(value = RoomApiExamples.JOIN_REQUEST)))
            @Valid @RequestBody JoinRoomRequest request) {
        return roomService.joinRoom(code, request);
    }
}
