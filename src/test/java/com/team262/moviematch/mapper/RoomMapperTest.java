package com.team262.moviematch.mapper;

import com.team262.moviematch.model.Participant;
import com.team262.moviematch.model.Room;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoomMapperTest {
    private final RoomMapper mapper = Mappers.getMapper(RoomMapper.class);

    @Test
    void creationResponseUsesIssuedTokenAndCopiesPreferences() {
        Room room = new Room("K7M2QX");
        room.getGenreIds().add(28);
        Participant host = participant(room);

        var response = mapper.toCreateResponse(room, host, "issued-token");
        room.getGenreIds().clear();

        assertThat(response.participant().id()).isEqualTo(7L);
        assertThat(response.participant().displayName()).isEqualTo("Host");
        assertThat(response.participant().host()).isTrue();
        assertThat(response.participant().token()).isEqualTo("issued-token");
        assertThat(response.preferences().genreIds()).containsExactly(28);
        assertThrows(UnsupportedOperationException.class,
                () -> response.preferences().genreIds().add(35));
    }

    @Test
    void lobbyResponseRetainsImmutableParticipantSnapshot() {
        Room room = new Room("K7M2QX");
        Participant host = participant(room);
        List<Participant> participants = new ArrayList<>(List.of(host));

        var response = mapper.toDetailsResponse(room, participants, 5);
        participants.clear();
        host.setName("Changed");

        assertThat(response.participantCount()).isEqualTo(1);
        assertThat(response.maxParticipants()).isEqualTo(5);
        assertThat(response.participants()).hasSize(1);
        assertThat(response.participants().getFirst().displayName()).isEqualTo("Host");
        assertThrows(UnsupportedOperationException.class, () -> response.participants().clear());
    }

    private Participant participant(Room room) {
        Participant host = new Participant(room, "Host", true, "stored-token", null);
        ReflectionTestUtils.setField(host, "id", 7L);
        return host;
    }
}
