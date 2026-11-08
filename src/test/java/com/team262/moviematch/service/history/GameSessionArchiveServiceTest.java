package com.team262.moviematch.service.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.team262.moviematch.model.*;
import com.team262.moviematch.model.history.GameSessionArchive;
import com.team262.moviematch.repository.*;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class GameSessionArchiveServiceTest {
    private final GameSessionArchiveRepository archives = mock(GameSessionArchiveRepository.class);
    private final ParticipantRepository participants = mock(ParticipantRepository.class);
    private final RoomTitleRepository cards = mock(RoomTitleRepository.class);
    private final VoteRepository votes = mock(VoteRepository.class);
    private final GameSessionArchiveService service =
            new GameSessionArchiveService(archives, participants, cards, votes);

    @Test
    void preservesAllCardsAndTiedWinnersWithoutCredentials() {
        Room room = completedRoom();
        Participant participant = new Participant(room, "Anna", true, "secret", LocalDateTime.now());
        ReflectionTestUtils.setField(participant, "id", 2L);
        when(participants.findAllByRoomIdOrderByIdAsc(1L)).thenReturn(List.of(participant));
        RoomTitle first = card(room, 3L, 0, 1);
        RoomTitle second = card(room, 4L, 1, 1);
        when(cards.findAllByRoomIdOrderByPositionAsc(1L)).thenReturn(List.of(first, second));

        service.archiveFinishedRoom(room);

        ArgumentCaptor<GameSessionArchive> saved =
                ArgumentCaptor.forClass(GameSessionArchive.class);
        verify(archives).save(saved.capture());
        assertThat(saved.getValue().getParticipants()).singleElement()
                .satisfies(member -> assertThat(member.getDisplayName()).isEqualTo("Anna"));
        assertThat(saved.getValue().getCards()).hasSize(2)
                .allSatisfy(card -> assertThat(card.isWinner()).isTrue());
        assertThat(saved.getValue().getFinishedAt()).isNotNull();
    }

    @Test
    void doesNotDuplicateExistingArchive() {
        when(archives.existsBySourceRoomId(1L)).thenReturn(true);
        service.archiveFinishedRoom(completedRoom());
        verify(archives, never()).save(any());
        verifyNoInteractions(participants, cards);
    }

    @Test
    void rejectsIncompleteRound() {
        assertThatThrownBy(() -> service.archiveFinishedRoom(new Room("ABC234")))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(archives);
    }

    @Test
    void doesNotChooseWinnerWhenThereAreNoLikes() {
        Room room = completedRoom();
        when(participants.findAllByRoomIdOrderByIdAsc(1L)).thenReturn(List.of(
                new Participant(room, "Anna", true, "secret", LocalDateTime.now())));
        when(cards.findAllByRoomIdOrderByPositionAsc(1L)).thenReturn(List.of(card(room, 3L, 0, 0)));
        service.archiveFinishedRoom(room);
        ArgumentCaptor<GameSessionArchive> saved =
                ArgumentCaptor.forClass(GameSessionArchive.class);
        verify(archives).save(saved.capture());
        assertThat(saved.getValue().getCards()).allSatisfy(card -> assertThat(card.isWinner()).isFalse());
    }

    private Room completedRoom() {
        Room room = new Room("ABC234");
        ReflectionTestUtils.setField(room, "id", 1L);
        room.setStatus(RoomStatus.FINISHED);
        room.setResultParticipantCount(1);
        return room;
    }

    private RoomTitle card(Room room, Long id, int position, int likes) {
        RoomTitle card = new RoomTitle(room, new Title(id, ContentType.MOVIES, "Movie"), position);
        ReflectionTestUtils.setField(card, "id", id);
        card.setResultLikesCount(likes);
        return card;
    }
}
