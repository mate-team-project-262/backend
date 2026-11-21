package com.team262.moviematch.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team262.moviematch.model.Participant;
import com.team262.moviematch.model.Room;
import com.team262.moviematch.model.RoomStatus;
import com.team262.moviematch.model.User;
import com.team262.moviematch.repository.ParticipantRepository;
import com.team262.moviematch.repository.RoomRepository;
import com.team262.moviematch.repository.UserRepository;
import com.team262.moviematch.service.account.AccountAvatarService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"title.source=stub",
        "spring.datasource.url=jdbc:h2:mem:room-avatar;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.liquibase.change-log=classpath:db/changelog/account-h2.yaml"})
@AutoConfigureMockMvc
class RoomAvatarIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired RoomRepository rooms;
    @Autowired ParticipantRepository participants;
    @Autowired UserRepository users;
    @Autowired AccountAvatarService avatars;

    @Test
    void showsLinkedAvatarAndRestrictsImageToRoomMembers() throws Exception {
        Room room = rooms.save(new Room("AVT123"));
        Room otherRoom = rooms.save(new Room("OTH123"));
        Participant viewer = participants.save(new Participant(room, "Viewer", true,
                "11111111-1111-1111-1111-111111111111", LocalDateTime.now()));
        Participant owner = participants.save(new Participant(room, "Owner", false,
                "22222222-2222-2222-2222-222222222222", LocalDateTime.now()));
        Participant outsider = participants.save(new Participant(otherRoom, "Outsider", true,
                "33333333-3333-3333-3333-333333333333", LocalDateTime.now()));
        User account = users.save(new User("room-avatar@example.com", "hash", "Owner"));
        owner.setUser(account);
        participants.saveAndFlush(owner);
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        avatars.upload(account, new MockMultipartFile("file", "owner.png", "image/png", png));
        String imagePath = "/rooms/AVT123/participants/" + owner.getId() + "/avatar";

        mvc.perform(get("/rooms/AVT123").header("X-Participant-Token", viewer.getToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants[0].avatarUrl").isEmpty())
                .andExpect(jsonPath("$.participants[1].avatarUrl").value(
                        org.hamcrest.Matchers.startsWith(imagePath + "?v=")));
        room.setStatus(RoomStatus.VOTING);
        rooms.saveAndFlush(room);
        mvc.perform(get("/rooms/AVT123/progress").header("X-Participant-Token", viewer.getToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants[1].avatarUrl").value(
                        org.hamcrest.Matchers.startsWith(imagePath + "?v=")));
        mvc.perform(get(imagePath).header("X-Participant-Token", viewer.getToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=3600, private"));
        mvc.perform(get(imagePath).header("X-Participant-Token", outsider.getToken()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/rooms/AVT123/participants/" + outsider.getId() + "/avatar")
                        .header("X-Participant-Token", viewer.getToken()))
                .andExpect(status().isNotFound());
    }
}
