package com.team262.moviematch.service.impl;

import com.team262.moviematch.dto.room.request.CreateRoomRequest;
import com.team262.moviematch.dto.room.request.JoinRoomRequest;
import com.team262.moviematch.dto.room.response.CreateRoomResponse;
import com.team262.moviematch.dto.room.response.JoinRoomResponse;
import com.team262.moviematch.dto.room.response.RoomDetailsResponse;
import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.ConflictException;
import com.team262.moviematch.exception.ex.ForbiddenException;
import com.team262.moviematch.exception.ex.NotFoundException;
import com.team262.moviematch.mapper.ParticipantMapper;
import com.team262.moviematch.mapper.RoomMapper;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.model.Room;
import com.team262.moviematch.model.RoomStatus;
import com.team262.moviematch.repository.ParticipantRepository;
import com.team262.moviematch.repository.RoomRepository;
import com.team262.moviematch.service.ParticipantTokenService;
import com.team262.moviematch.service.RoomService;
import com.team262.moviematch.util.RoomCodeGenerator;
import java.time.LocalDateTime;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RoomServiceImpl implements RoomService {
    private static final int CODE_GENERATION_ATTEMPTS = 5;

    private final RoomRepository roomRepository;
    private final ParticipantRepository participantRepository;
    private final ParticipantTokenService tokenService;
    private final RoomCodeGenerator codeGenerator;
    private final RoomMapper mapper;
    private final ParticipantMapper participantMapper;
    private final TransactionTemplate creationTransaction;
    private final int maxParticipants;

    public RoomServiceImpl(RoomRepository roomRepository,
                           ParticipantRepository participantRepository,
                           ParticipantTokenService tokenService, RoomCodeGenerator codeGenerator,
                           RoomMapper mapper, ParticipantMapper participantMapper, PlatformTransactionManager transactionManager,
                           @Value("${room.max-participants:5}") int maxParticipants) {
        this.roomRepository = roomRepository;
        this.participantRepository = participantRepository;
        this.tokenService = tokenService;
        this.codeGenerator = codeGenerator;
        this.mapper = mapper;
        this.participantMapper = participantMapper;
        this.maxParticipants = maxParticipants;
        this.creationTransaction = new TransactionTemplate(transactionManager);
        // A failed unique insert invalidates the transaction; retry the entire operation.
        this.creationTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public CreateRoomResponse createRoom(CreateRoomRequest request) {
        for (int attempt = 0; attempt < CODE_GENERATION_ATTEMPTS; attempt++) {
            try {
                return creationTransaction.execute(status -> {
                    Room room = roomRepository.saveAndFlush(mapper.toRoom(
                            codeGenerator.generateCode(), request));
                    Participant host = new Participant(room, request.displayName(), true, null, null);
                    String token = tokenService.issueToken(host);
                    return mapper.toCreateResponse(room, host, token);
                });
            } catch (DataIntegrityViolationException exception) {
                if (!isRoomCodeCollision(exception)) {
                    throw exception;
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Could not allocate a unique room code; please retry");
    }

    @Override
    @Transactional(readOnly = true)
    public RoomDetailsResponse getLobby(String code, Participant participant) {
        Room room = roomRepository.findByCode(code).orElseThrow(() ->
                new NotFoundException(ErrorCode.ROOM_NOT_FOUND, "Room not found"));
        if (!room.getId().equals(participant.getRoom().getId())) {
            throw new ForbiddenException(ErrorCode.WRONG_ROOM,
                    "Participant does not belong to this room");
        }
        return mapper.toDetailsResponse(room,
                participantRepository.findAllByRoomIdOrderByIdAsc(room.getId()), maxParticipants);
    }

    private boolean isRoomCodeCollision(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                String constraint = violation.getConstraintName();
                return constraint != null && (constraint.equalsIgnoreCase("uk_rooms_code")
                        || constraint.toLowerCase(java.util.Locale.ROOT).endsWith(".uk_rooms_code"));
            }
        }
        return false;
    }

    @Transactional
    public JoinRoomResponse joinRoom(String code, JoinRoomRequest request) {

        Room room = roomRepository.findByCode(code).orElseThrow(
                () -> new NotFoundException(ErrorCode.ROOM_NOT_FOUND,
                        "Room with code " + code + " doesn't exist"));

        validateRoom(room);
        validateDisplayName(room, request.displayName());

        String token = generateUniqueToken();

        Participant newParticipant = participantMapper.toParticipant(
                request, room, token, LocalDateTime.now());

        Participant savedParticipant = participantRepository.save(newParticipant);
        return participantMapper.toJoinRoomResponse(
                room,
                savedParticipant);
    }

    private String generateUniqueToken() {
        for (int attempt = 0; attempt < CODE_GENERATION_ATTEMPTS; attempt++) {
            String token = tokenService.generateToken();
            if (participantRepository.findByToken(token).isEmpty()) {
                return token;
            }
        }
        throw new IllegalStateException("Could not generate a unique participant token after "
                + CODE_GENERATION_ATTEMPTS + " attempts");
    }

    private void validateDisplayName(Room room, String displayName) {
        boolean nameTaken = participantRepository
                .existsByRoomAndNameIgnoreCase(room, displayName);
        if (nameTaken) {
            throw new ConflictException(ErrorCode.NAME_TAKEN,
                    "Name already has taken");
        }
    }

    private void validateRoom(Room room) {
        if (room.getStatus() != RoomStatus.LOBBY) {
            throw new ConflictException(ErrorCode.ROOM_NOT_JOINABLE,
                    "Room not in status LOBBY");
        }
        long participantsInRoom = participantRepository.countByRoom(room);
        if (participantsInRoom >= 5) {
            throw new ConflictException(ErrorCode.ROOM_FULL,
                    "Room has more than 5 participants");
        }
    }
}
