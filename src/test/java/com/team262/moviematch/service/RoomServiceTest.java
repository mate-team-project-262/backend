package com.team262.moviematch.service;

import com.team262.moviematch.dto.room.request.CreateRoomRequest;
import com.team262.moviematch.dto.room.request.PreferencesRequest;
import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.ForbiddenException;
import com.team262.moviematch.exception.ex.NotFoundException;
import com.team262.moviematch.mapper.RoomMapper;
import com.team262.moviematch.model.ContentType;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.model.Room;
import com.team262.moviematch.repository.ParticipantRepository;
import com.team262.moviematch.repository.RoomRepository;
import com.team262.moviematch.service.impl.RoomServiceImpl;
import com.team262.moviematch.util.RoomCodeGenerator;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {
    @Mock private RoomRepository rooms;
    @Mock private ParticipantRepository participants;
    @Mock private ParticipantTokenService tokens;
    @Mock private RoomCodeGenerator codes;
    @Mock private PlatformTransactionManager transactions;
    private RoomService service;

    @BeforeEach
    void setUp() {
        service = new RoomServiceImpl(rooms, participants, tokens, codes,
                Mappers.getMapper(RoomMapper.class), transactions, 5);
    }

    @Test
    void createsRoomWithDefaultsAndUnsavedHostForTokenService() {
        stubTransaction();
        when(rooms.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokens.issueToken(any())).thenAnswer(invocation -> {
            Participant host = invocation.getArgument(0);
            assertThat(host.getId()).isNull();
            assertThat(host.getToken()).isNull();
            assertThat(host.isHost()).isTrue();
            assertThat(host.getRoom().getCode()).isEqualTo("K7M2QX");
            ReflectionTestUtils.setField(host, "id", 7L);
            return "issued-token";
        });

        var response = service.createRoom(new CreateRoomRequest("Host", null));

        assertThat(response.participant().id()).isEqualTo(7L);
        assertThat(response.participant().token()).isEqualTo("issued-token");
        assertThat(response.preferences().contentType()).isEqualTo(new Room("K7M2QX").getContentType());
        assertThat(response.preferences().genreIds()).isEmpty();
        assertThat(response.preferences().maxDurationMinutes()).isNull();
        verifyNoInteractions(participants);
        verify(transactions).commit(any());
    }

    @Test
    void mapsSuppliedPreferencesWithoutSharingGenreSet() {
        RoomMapper mapper = Mappers.getMapper(RoomMapper.class);
        var request = new CreateRoomRequest("Host",
                new PreferencesRequest(ContentType.TV_SERIES, Set.of(28, 35), 120));
        Room room = mapper.toRoom("K7M2QX", request);
        assertThat(room.getContentType()).isEqualTo(ContentType.TV_SERIES);
        assertThat(room.getGenreIds()).containsExactlyInAnyOrder(28, 35);
        assertThat(room.getMaxDurationMinutes()).isEqualTo(120);
        room.getGenreIds().clear();
        assertThat(request.preferences().genreIds()).hasSize(2);
        assertThat(mapper.toRoom("K7M2QX", new CreateRoomRequest("Host",
                new PreferencesRequest(ContentType.MOVIES, null, null))).getGenreIds()).isEmpty();
    }

    @Test
    void retriesOnlyRoomCodeCollisionAfterRollback() {
        stubTransaction();
        when(rooms.saveAndFlush(any())).thenThrow(constraint("rooms.uk_rooms_code"))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(tokens.issueToken(any())).thenAnswer(invocation -> {
            Participant host = invocation.getArgument(0);
            ReflectionTestUtils.setField(host, "id", 7L);
            return "issued-token";
        });

        assertThat(service.createRoom(new CreateRoomRequest("Host", null)).participant().token())
                .isEqualTo("issued-token");
        verify(transactions, times(2)).getTransaction(argThat(definition ->
                definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        verify(transactions).rollback(any());
        verify(transactions).commit(any());
        verify(tokens).issueToken(any());
    }

    @Test
    void exhaustsFiveCodeAttemptsWithoutIssuingToken() {
        stubTransaction();
        when(rooms.saveAndFlush(any())).thenThrow(constraint("uk_rooms_code"));
        var error = assertThrows(ResponseStatusException.class,
                () -> service.createRoom(new CreateRoomRequest("Host", null)));
        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verify(codes, times(5)).generateCode();
        verify(transactions, times(5)).rollback(any());
        verify(transactions, never()).commit(any());
        verifyNoInteractions(tokens, participants);
    }

    @Test
    void rollsBackWithoutRetryingParticipantTokenCollision() {
        stubTransaction();
        when(rooms.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doAnswer(invocation -> { throw constraint("participants.uk_participants_token"); })
                .when(tokens).issueToken(any());
        assertThrows(DataIntegrityViolationException.class,
                () -> service.createRoom(new CreateRoomRequest("Host", null)));
        verify(codes).generateCode();
        verify(transactions).rollback(any());
        verify(transactions, never()).commit(any());
    }

    @Test
    void returnsLobbyForRoomMemberWithCountAndLimit() {
        Room room = room(1L);
        Participant host = new Participant(room, "Host", true, "secret", null);
        ReflectionTestUtils.setField(host, "id", 7L);
        when(rooms.findByCode("K7M2QX")).thenReturn(Optional.of(room));
        when(participants.findAllByRoomIdOrderByIdAsc(1L)).thenReturn(List.of(host));
        var details = service.getLobby("K7M2QX", host);
        assertThat(details.participantCount()).isEqualTo(1);
        assertThat(details.maxParticipants()).isEqualTo(5);
        assertThat(details.participants().getFirst().displayName()).isEqualTo("Host");
        assertThat(details.participants().getFirst().id()).isEqualTo(7L);
    }

    @Test
    void rejectsOtherRoomBeforeLoadingParticipants() {
        when(rooms.findByCode("K7M2QX")).thenReturn(Optional.of(room(1L)));
        Participant other = new Participant(room(2L), "Other", false, "secret", null);
        var error = assertThrows(ForbiddenException.class, () -> service.getLobby("K7M2QX", other));
        assertThat(error.getCode()).isEqualTo(ErrorCode.WRONG_ROOM);
        verifyNoInteractions(participants);
    }

    @Test
    void reportsMissingRoom() {
        when(rooms.findByCode("K7M2QX")).thenReturn(Optional.empty());
        var error = assertThrows(NotFoundException.class, () -> service.getLobby("K7M2QX",
                new Participant(room(1L), "Host", true, "secret", null)));
        assertThat(error.getCode()).isEqualTo(ErrorCode.ROOM_NOT_FOUND);
        verifyNoInteractions(participants);
    }

    private void stubTransaction() {
        when(transactions.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        when(codes.generateCode()).thenReturn("K7M2QX");
    }

    private Room room(long id) {
        Room room = new Room("K7M2QX");
        ReflectionTestUtils.setField(room, "id", id);
        return room;
    }

    private DataIntegrityViolationException constraint(String name) {
        return new DataIntegrityViolationException("Constraint violation",
                new ConstraintViolationException("Duplicate", new SQLException("Duplicate", "23000", 1062), name));
    }
}
