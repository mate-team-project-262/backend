package com.team262.moviematch.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team262.moviematch.MySqlTestConfiguration;
import com.team262.moviematch.model.ContentType;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.model.Room;
import com.team262.moviematch.model.RoomStatus;
import com.team262.moviematch.model.RoomTitle;
import com.team262.moviematch.model.Title;
import com.team262.moviematch.model.Vote;
import com.team262.moviematch.repository.ParticipantRepository;
import com.team262.moviematch.repository.RoomRepository;
import com.team262.moviematch.repository.RoomTitleRepository;
import com.team262.moviematch.repository.TitleRepository;
import com.team262.moviematch.repository.VoteRepository;
import com.team262.moviematch.service.ParticipantTokenService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = {
        "tmdb.base-url=https://example.invalid", "tmdb.api-key=test-placeholder",
        "room.max-participants=5"
})
@AutoConfigureMockMvc
@Import(MySqlTestConfiguration.class)
class GameProgressIntegrationTest {

    private static final String CODE = "K7M2QX";
    private static final int CARDS = 3;

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private RoomRepository rooms;
    @Autowired private ParticipantRepository participants;
    @Autowired private TitleRepository titles;
    @Autowired private RoomTitleRepository roomTitles;
    @Autowired private VoteRepository votes;
    @Autowired private ParticipantTokenService tokenService;

    private Room room;
    private final List<Participant> people = new ArrayList<>();
    private final List<String> tokens = new ArrayList<>();
    private final List<RoomTitle> cards = new ArrayList<>();

    @BeforeEach
    void clean() {
        people.clear();
        tokens.clear();
        cards.clear();
        votes.deleteAllInBatch();
        roomTitles.deleteAllInBatch();
        rooms.deleteAllInBatch();
        titles.deleteAllInBatch();
    }

    private void fixture(RoomStatus status) {
        Room r = new Room(CODE);
        r.setStatus(status);
        room = rooms.saveAndFlush(r);
        String[] names = {"Sofia", "Anna", "Peter", "Alex"};
        for (int i = 0; i < names.length; i++) {
            Participant p = new Participant(room, names[i], i == 0, null, null);
            tokens.add(tokenService.issueToken(p));
            people.add(p);
        }
        for (int i = 0; i < CARDS; i++) {
            Title title = titles.save(new Title(9_000_000L + i, ContentType.MOVIES, "Title " + i));
            cards.add(roomTitles.save(new RoomTitle(room, title, i)));
        }
    }

    private void vote(int person, int card, boolean liked) {
        votes.save(new Vote(people.get(person), cards.get(card), liked));
    }

    private void voteAll(int person) {
        for (int card = 0; card < CARDS; card++) {
            vote(person, card, card % 2 == 0);
        }
    }

    private ResultActions progress(String token) throws Exception {
        return mvc.perform(get("/rooms/" + CODE + "/progress").header("X-Participant-Token", token));
    }

    @Test
    void threeOfFourFinished_showsPerParticipantState() throws Exception {
        fixture(RoomStatus.VOTING);
        voteAll(0);
        voteAll(1);
        voteAll(2);
        vote(3, 0, true);

        progress(tokens.get(3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finishedParticipants").value(3))
                .andExpect(jsonPath("$.totalParticipants").value(4))
                .andExpect(jsonPath("$.myFinished").value(false))
                .andExpect(jsonPath("$.allFinished").value(false))
                .andExpect(jsonPath("$.participants.length()").value(4))
                .andExpect(jsonPath("$.participants[0].displayName").value("Sofia"))
                .andExpect(jsonPath("$.participants[0].host").value(true))
                .andExpect(jsonPath("$.participants[0].finished").value(true))
                .andExpect(jsonPath("$.participants[3].id").value(people.get(3).getId()))
                .andExpect(jsonPath("$.participants[3].finished").value(false));
    }

    @Test
    void myFinishedDependsOnTheToken_andCodeIsCaseInsensitive() throws Exception {
        fixture(RoomStatus.VOTING);
        voteAll(0);

        progress(tokens.get(0)).andExpect(jsonPath("$.myFinished").value(true));
        mvc.perform(get("/rooms/k7m2qx/progress").header("X-Participant-Token", tokens.get(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myFinished").value(false));
    }

    @Test
    void allFinishedAfterLastVote_andRoomStatusStaysVoting() throws Exception {
        fixture(RoomStatus.VOTING);
        voteAll(0);
        voteAll(1);
        voteAll(2);
        vote(3, 0, true);
        progress(tokens.get(0)).andExpect(jsonPath("$.allFinished").value(false));

        vote(3, 1, false);
        vote(3, 2, true);

        progress(tokens.get(0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finishedParticipants").value(4))
                .andExpect(jsonPath("$.allFinished").value(true));
        assertThat(rooms.findByCode(CODE).orElseThrow().getStatus()).isEqualTo(RoomStatus.VOTING);
    }

    @Test
    void responseNeverContainsTokens() throws Exception {
        fixture(RoomStatus.VOTING);
        voteAll(0);

        String body = progress(tokens.get(1)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode tree = json.readTree(body);
        assertThat(tree.findValues("token")).isEmpty();
        assertThat(body).doesNotContain("tokenCreatedAt");
        tokens.forEach(token -> assertThat(body).doesNotContain(token));
    }

    @Test
    void lobbyReturnsRoomNotVoting() throws Exception {
        fixture(RoomStatus.LOBBY);

        progress(tokens.get(0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_VOTING"));
    }

    @Test
    void finishedRoomStillReturnsProgress() throws Exception {
        fixture(RoomStatus.FINISHED);
        voteAll(0);

        progress(tokens.get(0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finishedParticipants").value(1));
    }

    @Test
    void rejectsMissingAndUnknownTokens() throws Exception {
        fixture(RoomStatus.VOTING);

        mvc.perform(get("/rooms/" + CODE + "/progress"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        progress("unknown-token")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void tokenOfAnotherRoomIsForbidden_andUnknownRoomIs404() throws Exception {
        fixture(RoomStatus.VOTING);
        Room other = rooms.saveAndFlush(new Room("ABCD23"));
        String otherToken = tokenService.issueToken(new Participant(other, "Zed", true, null, null));

        progress(otherToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WRONG_ROOM"));
        mvc.perform(get("/rooms/ZZZZZZ/progress").header("X-Participant-Token", tokens.get(0)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
    }
}