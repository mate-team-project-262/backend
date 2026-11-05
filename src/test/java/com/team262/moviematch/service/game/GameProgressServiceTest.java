package com.team262.moviematch.service.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.team262.moviematch.dto.game.GameProgressResponse;
import com.team262.moviematch.dto.game.ParticipantProgressDto;
import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.ConflictException;
import com.team262.moviematch.exception.ex.ForbiddenException;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.model.Room;
import com.team262.moviematch.model.RoomStatus;
import com.team262.moviematch.repository.ParticipantRepository;
import com.team262.moviematch.repository.ParticipantVoteCount;
import com.team262.moviematch.repository.RoomTitleRepository;
import com.team262.moviematch.repository.VoteRepository;
import com.team262.moviematch.service.ParticipantAccessService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GameProgressServiceTest {

    private static final String CODE = "K7M2QX";
    private static final long CARDS = 20;

    @Mock ParticipantAccessService accessService;
    @Mock ParticipantRepository participantRepository;
    @Mock RoomTitleRepository roomTitleRepository;
    @Mock VoteRepository voteRepository;
    @InjectMocks GameProgressService service;

    private Room room(RoomStatus status) {
        Room room = new Room(CODE);
        ReflectionTestUtils.setField(room, "id", 1L);
        room.setStatus(status);
        return room;
    }

    private Participant participant(Room room, long id, String name, boolean host) {
        Participant p = new Participant(room, name, host, "secret-" + id, LocalDateTime.now());
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private List<Participant> four(Room room) {
        return List.of(participant(room, 1, "Sofia", true), participant(room, 2, "Anna", false),
                participant(room, 3, "Peter", false), participant(room, 4, "Alex", false));
    }

    private ParticipantVoteCount votes(long participantId, long count) {
        return new ParticipantVoteCount(participantId, count);
    }

    private void stub(Room room, List<Participant> all, long cards, ParticipantVoteCount... counts) {
        when(accessService.requireRoomMembership(eq(CODE), any())).thenReturn(room);
        when(roomTitleRepository.countByRoomId(1L)).thenReturn(cards);
        when(participantRepository.findAllByRoomIdOrderByIdAsc(1L)).thenReturn(all);
        when(voteRepository.countVotesByParticipantInRoom(1L)).thenReturn(List.of(counts));
    }

    @Test
    void threeOutOfFourFinished() {
        Room room = room(RoomStatus.VOTING);
        List<Participant> all = four(room);
        stub(room, all, CARDS, votes(1, 20), votes(2, 20), votes(3, 20), votes(4, 7));

        GameProgressResponse response = service.getProgress(CODE, all.get(3));

        assertThat(response.finishedParticipants()).isEqualTo(3);
        assertThat(response.totalParticipants()).isEqualTo(4);
        assertThat(response.allFinished()).isFalse();
        assertThat(response.participants()).extracting(ParticipantProgressDto::id)
                .containsExactly(1L, 2L, 3L, 4L);
        assertThat(response.participants()).extracting(ParticipantProgressDto::finished)
                .containsExactly(true, true, true, false);
        assertThat(response.participants()).extracting(ParticipantProgressDto::displayName)
                .containsExactly("Sofia", "Anna", "Peter", "Alex");
        assertThat(response.participants()).extracting(ParticipantProgressDto::host)
                .containsExactly(true, false, false, false);
    }

    @Test
    void myFinishedDependsOnWhoAsks() {
        Room room = room(RoomStatus.VOTING);
        List<Participant> all = four(room);
        stub(room, all, CARDS, votes(1, 20), votes(4, 7));

        assertThat(service.getProgress(CODE, all.get(0)).myFinished()).isTrue();
        assertThat(service.getProgress(CODE, all.get(3)).myFinished()).isFalse();
    }

    @Test
    void allFinishedAfterLastVote() {
        Room room = room(RoomStatus.VOTING);
        List<Participant> all = four(room);
        stub(room, all, CARDS, votes(1, 20), votes(2, 20), votes(3, 20), votes(4, 20));

        GameProgressResponse response = service.getProgress(CODE, all.get(3));

        assertThat(response.finishedParticipants()).isEqualTo(4);
        assertThat(response.allFinished()).isTrue();
        assertThat(response.myFinished()).isTrue();
        assertThat(room.getStatus()).isEqualTo(RoomStatus.VOTING);
    }

    @Test
    void participantWithoutAnyVoteIsNotFinished() {
        Room room = room(RoomStatus.VOTING);
        List<Participant> all = four(room);
        stub(room, all, CARDS, votes(1, 20));

        GameProgressResponse response = service.getProgress(CODE, all.get(1));

        assertThat(response.finishedParticipants()).isEqualTo(1);
        assertThat(response.myFinished()).isFalse();
    }

    @Test
    void roomWithoutCardsIsNeverFinished() {
        Room room = room(RoomStatus.VOTING);
        List<Participant> all = four(room);
        stub(room, all, 0);

        GameProgressResponse response = service.getProgress(CODE, all.get(0));

        assertThat(response.finishedParticipants()).isZero();
        assertThat(response.allFinished()).isFalse();
        assertThat(response.myFinished()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = RoomStatus.class, names = {"VOTING", "FINISHED"})
    void votingAndFinishedRoomsReturnProgress(RoomStatus status) {
        Room room = room(status);
        List<Participant> all = four(room);
        stub(room, all, CARDS, votes(1, 20));

        assertThat(service.getProgress(CODE, all.get(0)).totalParticipants()).isEqualTo(4);
    }

    @Test
    void lobbyIsRejectedBeforeAnyQuery() {
        Room room = room(RoomStatus.LOBBY);
        Participant p = participant(room, 1, "Sofia", true);
        when(accessService.requireRoomMembership(CODE, p)).thenReturn(room);

        assertThatThrownBy(() -> service.getProgress(CODE, p))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ROOM_NOT_VOTING));
        verifyNoInteractions(participantRepository, roomTitleRepository, voteRepository);
    }

    @Test
    void foreignRoomFailsBeforeAnyQuery() {
        Participant p = participant(room(RoomStatus.VOTING), 1, "Sofia", true);
        when(accessService.requireRoomMembership(CODE, p))
                .thenThrow(new ForbiddenException(ErrorCode.WRONG_ROOM, "x"));

        assertThatThrownBy(() -> service.getProgress(CODE, p))
                .isInstanceOfSatisfying(ForbiddenException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.WRONG_ROOM));
        verifyNoInteractions(participantRepository, roomTitleRepository, voteRepository);
    }
}