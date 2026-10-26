package com.team262.moviematch.mapper;

import com.team262.moviematch.dto.room.request.CreateRoomRequest;
import com.team262.moviematch.dto.room.response.CreateRoomResponse;
import com.team262.moviematch.dto.room.response.ParticipantDto;
import com.team262.moviematch.dto.room.response.ParticipantWithTokenDto;
import com.team262.moviematch.dto.room.response.PreferencesResponse;
import com.team262.moviematch.dto.room.response.RoomDetailsResponse;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.model.Room;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class RoomMapper {
    public Room toRoom(String code, CreateRoomRequest request) {
        Room room = new Room(code);
        if (request.preferences() != null) {
            room.setContentType(request.preferences().contentType());
            room.setMaxDurationMinutes(request.preferences().maxDurationMinutes());
            if (request.preferences().genreIds() != null) {
                room.getGenreIds().addAll(request.preferences().genreIds());
            }
        }
        return room;
    }

    public CreateRoomResponse toCreateResponse(Room room, Participant host, String token) {
        return new CreateRoomResponse(room.getCode(), room.getStatus(), preferences(room),
                new ParticipantWithTokenDto(host.getId(), host.getName(), host.isHost(), token));
    }

    public RoomDetailsResponse toDetailsResponse(Room room, List<Participant> participants,
                                                int maxParticipants) {
        List<ParticipantDto> participantDtos = participants.stream()
                .map(participant -> new ParticipantDto(participant.getId(),
                        participant.getName(), participant.isHost()))
                .toList();
        return new RoomDetailsResponse(room.getCode(), room.getStatus(), preferences(room),
                participantDtos.size(), maxParticipants, participantDtos);
    }

    private PreferencesResponse preferences(Room room) {
        return new PreferencesResponse(room.getContentType(), Set.copyOf(room.getGenreIds()),
                room.getMaxDurationMinutes());
    }
}
