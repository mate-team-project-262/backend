package com.team262.moviematch.service;

import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.ForbiddenException;
import com.team262.moviematch.exception.ex.NotFoundException;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.model.Room;
import com.team262.moviematch.repository.RoomRepository;
import com.team262.moviematch.service.impl.ParticipantAccessServiceImpl;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParticipantAccessServiceImplTest {

    @Mock
    ParticipantTokenService tokenService;
    @Mock
    RoomRepository roomRepository;
    @InjectMocks
    ParticipantAccessServiceImpl service;

    private Room room(long id, String code) {
        Room room = new Room(code);
        ReflectionTestUtils.setField(room, "id", id);
        return room;
    }

    private Participant participantIn(Room room) {
        return new Participant(room, "Anna", false, "tok", LocalDateTime.now());
    }

    @Test
    void returnsRoomWhenParticipantBelongsToIt() {
        Room room = room(1L, "K7M2QX");
        when(roomRepository.findByCode("K7M2QX")).thenReturn(Optional.of(room));

        assertThat(service.requireRoomMembership("K7M2QX", participantIn(room))).isSameAs(room);
    }

    @Test
    void unknownRoom_is404() {
        when(roomRepository.findByCode("ZZZZZZ")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireRoomMembership("ZZZZZZ", participantIn(room(1L, "K7M2QX"))))
                .isInstanceOfSatisfying(NotFoundException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ROOM_NOT_FOUND));
    }

    @Test
    void participantFromOtherRoom_is403() {
        Room other = room(2L, "ABCDEF");
        when(roomRepository.findByCode("ABCDEF")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.requireRoomMembership("ABCDEF", participantIn(room(1L, "K7M2QX"))))
                .isInstanceOfSatisfying(ForbiddenException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.WRONG_ROOM));
    }
}