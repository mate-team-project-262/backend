package com.team262.moviematch.mapper;

import com.team262.moviematch.config.MapperConfig;
import com.team262.moviematch.dto.room.request.JoinRoomRequest;
import com.team262.moviematch.dto.room.response.JoinRoomResponse;
import com.team262.moviematch.dto.room.response.ParticipantWithTokenDto;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.model.Room;
import java.time.LocalDateTime;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapperConfig.class)
public interface ParticipantMapper {

    @Mapping(target = "name", source = "request.displayName")
    @Mapping(target = "room", source = "room")
    @Mapping(target = "token", source = "token")
    @Mapping(target = "tokenCreatedAt", source = "tokenCreatedAt")
    @Mapping(target = "host", constant = "false")
    @Mapping(target = "id", ignore = true)
    Participant toParticipant(
            JoinRoomRequest request,
            Room room,
            String token,
            LocalDateTime tokenCreatedAt
    );

    @Mapping(target = "displayName", source = "name")
    ParticipantWithTokenDto toParticipantWithTokenDto(
            Participant participant
    );

    @Mapping(target = "code", source = "room.code")
    @Mapping(target = "status", source = "room.status")
    @Mapping(target = "currentParticipant", source = "participant")
    JoinRoomResponse toJoinRoomResponse(
            Room room,
            Participant participant
    );
}
