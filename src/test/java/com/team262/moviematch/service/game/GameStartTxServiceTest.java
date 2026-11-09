package com.team262.moviematch.service.game;

import com.team262.moviematch.dto.room.response.StartGameResponse;
import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.ConflictException;
import com.team262.moviematch.exception.ex.ForbiddenException;
import com.team262.moviematch.model.ContentType;
import com.team262.moviematch.model.DeckBuildStatus;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.model.Room;
import com.team262.moviematch.model.RoomStatus;
import com.team262.moviematch.model.RoomTitle;
import com.team262.moviematch.model.Title;
import com.team262.moviematch.repository.ParticipantRepository;
import com.team262.moviematch.repository.RoomRepository;
import com.team262.moviematch.repository.RoomTitleRepository;
import com.team262.moviematch.repository.TitleRepository;
import com.team262.moviematch.service.ParticipantAccessService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameStartTxServiceTest {

    @Mock
    ParticipantAccessService accessService;
    @Mock
    RoomRepository roomRepository;
    @Mock
    ParticipantRepository participantRepository;
    @Mock
    RoomTitleRepository roomTitleRepository;
    @Mock
    TitleRepository titleRepository;
    @InjectMocks
    GameStartTxService service;

    private Room room() {
        Room room = new Room("K7M2QX");
        ReflectionTestUtils.setField(room, "id", 1L);
        return room;
    }

    private Participant participant(Room room, boolean host) {
        return new Participant(room, "Anna", host, "tok", LocalDateTime.now());
    }

    private ErrorCode codeOf(Throwable t) {
        if (t instanceof ConflictException e) return e.getCode();
        if (t instanceof ForbiddenException e) return e.getCode();
        throw new AssertionError(t);
    }

    @Test
    void nonHost_isForbidden() {
        Room room = room();
        Participant p = participant(room, false);

        when(accessService.requireRoomMembershipForUpdate("K7M2QX", p))
                .thenReturn(p);

        assertThatThrownBy(() -> service.claimStart("K7M2QX", p))
                .satisfies(t -> assertThat(codeOf(t))
                        .isEqualTo(ErrorCode.NOT_HOST));

        verify(roomRepository, never()).claimDeckBuild(any());
    }

    @Test
    void roomAlreadyVoting_isConflict() {
        Room room = room();
        room.setStatus(RoomStatus.VOTING);

        Participant p = participant(room, true);

        when(accessService.requireRoomMembershipForUpdate("K7M2QX", p))
                .thenReturn(p);

        assertThatThrownBy(() -> service.claimStart("K7M2QX", p))
                .satisfies(t -> assertThat(codeOf(t))
                        .isEqualTo(ErrorCode.ROOM_ALREADY_STARTED));
    }

    @Test
    void singleParticipant_isConflict() {
        Room room = room();
        Participant p = participant(room, true);

        when(accessService.requireRoomMembershipForUpdate("K7M2QX", p))
                .thenReturn(p);

        when(participantRepository.countByRoom(room))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.claimStart("K7M2QX", p))
                .satisfies(t -> assertThat(codeOf(t))
                        .isEqualTo(ErrorCode.NOT_ENOUGH_PARTICIPANTS));
    }

    @Test
    void lostClaim_isConflict() {
        Room room = room();
        Participant p = participant(room, true);

        when(accessService.requireRoomMembershipForUpdate("K7M2QX", p))
                .thenReturn(p);

        when(participantRepository.countByRoom(room))
                .thenReturn(2L);

        when(roomRepository.claimDeckBuild(1L))
                .thenReturn(0);

        assertThatThrownBy(() -> service.claimStart("K7M2QX", p))
                .satisfies(t -> assertThat(codeOf(t))
                        .isEqualTo(ErrorCode.ROOM_ALREADY_STARTED));
    }

    @Test
    void successfulClaim_returnsPreferencesSnapshot() {
        Room room = room();
        room.setContentType(ContentType.TV_SERIES);
        room.setMaxDurationMinutes(60);
        room.getGenreIds().add(18);

        Participant p = participant(room, true);

        when(accessService.requireRoomMembershipForUpdate("K7M2QX", p))
                .thenReturn(p);

        when(participantRepository.countByRoom(room))
                .thenReturn(2L);

        when(roomRepository.claimDeckBuild(1L))
                .thenReturn(1);

        StartPlan plan = service.claimStart("K7M2QX", p);

        assertThat(plan.roomId()).isEqualTo(1L);
        assertThat(plan.contentType()).isEqualTo(ContentType.TV_SERIES);
        assertThat(plan.genreIds()).containsExactly(18);
        assertThat(plan.maxDurationMinutes()).isEqualTo(60);
    }

    @Test
    void completeStart_savesTwentyCardsAndSwitchesToVoting() {
        Room room = room();
        room.setDeckBuildStatus(DeckBuildStatus.BUILDING);

        when(roomRepository.findLockedById(1L))
                .thenReturn(Optional.of(room));

        when(titleRepository.getReferenceById(anyLong()))
                .thenAnswer(inv ->
                        new Title(inv.getArgument(0), ContentType.MOVIES, "t"));

        List<Long> ids = LongStream.rangeClosed(1, 20)
                .boxed()
                .toList();

        StartGameResponse response = service.completeStart(1L, ids);

        assertThat(response.status()).isEqualTo(RoomStatus.VOTING);
        assertThat(response.deckSize()).isEqualTo(20);
        assertThat(room.getDeckBuildStatus()).isEqualTo(DeckBuildStatus.READY);
        assertThat(room.getStartedAt()).isNotNull();

        ArgumentCaptor<List<RoomTitle>> captor =
                ArgumentCaptor.forClass(List.class);

        verify(roomTitleRepository).saveAll(captor.capture());

        assertThat(captor.getValue())
                .extracting(RoomTitle::getPosition)
                .containsExactlyElementsOf(
                        IntStream.range(0, 20).boxed().toList());
    }

    @Test
    void completeStart_whenNotBuilding_isConflict() {
        Room room = room();

        when(roomRepository.findLockedById(1L))
                .thenReturn(Optional.of(room));

        assertThatThrownBy(() -> service.completeStart(
                1L,
                LongStream.rangeClosed(1, 20).boxed().toList()))
                .satisfies(t -> assertThat(codeOf(t))
                        .isEqualTo(ErrorCode.ROOM_ALREADY_STARTED));

        verify(roomTitleRepository, never()).saveAll(any());
    }
}