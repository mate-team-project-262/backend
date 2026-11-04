package com.team262.moviematch.mapper;

import com.team262.moviematch.dto.room.request.CreateRoomRequest;
import com.team262.moviematch.dto.room.request.PreferencesRequest;
import com.team262.moviematch.dto.room.response.CreateRoomResponse;
import com.team262.moviematch.dto.room.response.ParticipantDto;
import com.team262.moviematch.dto.room.response.ParticipantWithTokenDto;
import com.team262.moviematch.dto.room.response.PreferencesResponse;
import com.team262.moviematch.dto.room.response.RoomDetailsResponse;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.model.Room;
import java.util.List;
import java.util.Set;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR,
        imports = {List.class, Set.class})
public interface RoomMapper {
    default Room toRoom(String code, CreateRoomRequest request) {
        Room room = new Room(code);
        if (request.preferences() != null) {
            applyPreferences(request.preferences(), room);
        }
        return room;
    }

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "contentType", source = "contentType")
    @Mapping(target = "genreIds", source = "genreIds")
    @Mapping(target = "maxDurationMinutes", source = "maxDurationMinutes")
    void applyPreferences(PreferencesRequest preferences, @MappingTarget Room room);

    @Mapping(target = "displayName", source = "name")
    ParticipantDto toParticipant(Participant participant);

    @Mapping(target = "displayName", source = "participant.name")
    @Mapping(target = "token", source = "token")
    ParticipantWithTokenDto toParticipantWithToken(Participant participant, String token);

    @Mapping(target = "genreIds", expression = "java(Set.copyOf(room.getGenreIds()))")
    PreferencesResponse toPreferences(Room room);

    List<ParticipantDto> toParticipants(List<Participant> participants);

    @Mapping(target = "code", source = "room.code")
    @Mapping(target = "status", source = "room.status")
    @Mapping(target = "preferences", source = "room")
    @Mapping(target = "participant", expression = "java(toParticipantWithToken(host, token))")
    CreateRoomResponse toCreateResponse(Room room, Participant host, String token);

    @Mapping(target = "code", source = "room.code")
    @Mapping(target = "status", source = "room.status")
    @Mapping(target = "preferences", source = "room")
    @Mapping(target = "participantCount", expression = "java(participants.size())")
    @Mapping(target = "maxParticipants", source = "maxParticipants")
    @Mapping(target = "participants", expression = "java(List.copyOf(toParticipants(participants)))")
    RoomDetailsResponse toDetailsResponse(Room room, List<Participant> participants, int maxParticipants);
}
