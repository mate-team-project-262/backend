package com.team262.moviematch.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team262.moviematch.model.RoomTitle;
import com.team262.moviematch.model.analytics.AnalyticsEvent;
import com.team262.moviematch.model.analytics.EventSource;
import com.team262.moviematch.repository.AnalyticsEventRepository;
import com.team262.moviematch.repository.ParticipantRepository;
import com.team262.moviematch.repository.RoomRepository;
import com.team262.moviematch.repository.RoomTitleRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** The whole flow on the stub title source: create, join, start, vote, finish. */
@SpringBootTest(properties = {"title.source=stub",
        "spring.liquibase.change-log=classpath:db/changelog/account-h2.yaml",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.datasource.url=jdbc:h2:mem:accounts;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password="})
@AutoConfigureMockMvc
class BackendAnalyticsEventsIntegrationTest {

    private static final String TOKEN = "X-Participant-Token";
    private static final int CARDS = 20;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AnalyticsEventRepository events;
    @Autowired ParticipantRepository participants;
    @Autowired RoomRepository rooms;
    @Autowired RoomTitleRepository roomTitles;

    private record Game(String code, String hostToken, String guestToken, Long roomId, String gameSessionId,
                        Long hostParticipantId, Long guestParticipantId, List<Long> cardIds, List<Long> tmdbIds) {
    }

    @BeforeEach
    void clean() {
        events.deleteAllInBatch();
    }

    private String body(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }

    private ResultActions voteRaw(String code, String token, long roomTitleId, boolean liked) throws Exception {
        return mvc.perform(post("/rooms/" + code + "/votes").header(TOKEN, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomTitleId\":" + roomTitleId + ",\"isLiked\":" + liked + "}"));
    }

    /** Plays a whole game: the host votes on every card, then the guest does. likes = indexes of liked cards. */
    private Game play(Set<Integer> hostLikes, Set<Integer> guestLikes) throws Exception {
        JsonNode created = json.readTree(body(mvc.perform(post("/rooms").contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Host\"}")).andExpect(status().isCreated())));
        String code = created.get("code").asText();
        String hostToken = created.at("/participant/token").asText();
        JsonNode joined = json.readTree(body(mvc.perform(post("/rooms/" + code + "/join")
                .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Guest\"}"))
                .andExpect(status().isCreated())));
        String guestToken = joined.at("/currentParticipant/token").asText();
        mvc.perform(post("/rooms/" + code + "/start").header(TOKEN, hostToken)).andExpect(status().isOk());

        JsonNode deck = json.readTree(body(mvc.perform(get("/rooms/" + code + "/deck").header(TOKEN, hostToken))
                .andExpect(status().isOk())));
        List<Long> cardIds = new ArrayList<>();
        deck.get("cards").forEach(card -> cardIds.add(card.get("roomTitleId").asLong()));
        assertThat(cardIds).hasSize(CARDS);

        for (int i = 0; i < CARDS; i++) {
            voteRaw(code, hostToken, cardIds.get(i), hostLikes.contains(i)).andExpect(status().isOk());
        }
        for (int i = 0; i < CARDS; i++) {
            voteRaw(code, guestToken, cardIds.get(i), guestLikes.contains(i)).andExpect(status().isOk());
        }

        Long hostId = participants.findByToken(hostToken).orElseThrow().getId();
        Long guestId = participants.findByToken(guestToken).orElseThrow().getId();
        Long roomId = participants.findByToken(hostToken).orElseThrow().getRoom().getId();
        String gameSessionId = rooms.findByCodeIgnoreCase(code).orElseThrow().getGameSessionId();
        List<Long> tmdbIds = roomTitles.findAllByRoomIdOrderByPositionAsc(roomId).stream()
                .map(RoomTitle::getTitle).map(title -> title.getTmdbId()).toList();
        return new Game(code, hostToken, guestToken, roomId, gameSessionId, hostId, guestId, cardIds, tmdbIds);
    }

    private List<AnalyticsEvent> backend(Game game, String name) {
        return events.findAll().stream()
                .filter(event -> event.getSource() == EventSource.BACKEND)
                .filter(event -> game.roomId().equals(event.getRoomId()))
                .filter(event -> name.equals(event.getName()))
                .toList();
    }

    private JsonNode props(AnalyticsEvent event) throws Exception {
        return json.readTree(event.getPropertiesJson());
    }

    @Test
    void aGameWithOneWinnerRecordsStartsCompletionsAndTheWinner() throws Exception {
        Game game = play(Set.of(0, 1), Set.of(0));

        assertThat(game.gameSessionId()).isNotBlank();

        List<AnalyticsEvent> started = backend(game, "swipe_session_started");
        assertThat(started).extracting(AnalyticsEvent::getParticipantId)
                .containsExactlyInAnyOrder(game.hostParticipantId(), game.guestParticipantId());
        assertThat(started).allSatisfy(event -> {
            assertThat(event.getGameSessionId()).isEqualTo(game.gameSessionId());
            assertThat(event.getClientSessionId()).isNull();
            assertThat(props(event).get("total_cards").asInt()).isEqualTo(CARDS);
        });

        List<AnalyticsEvent> completed = backend(game, "swipe_session_completed");
        assertThat(completed).hasSize(2);
        AnalyticsEvent hostCompleted = completed.stream()
                .filter(event -> game.hostParticipantId().equals(event.getParticipantId())).findFirst().orElseThrow();
        AnalyticsEvent guestCompleted = completed.stream()
                .filter(event -> game.guestParticipantId().equals(event.getParticipantId())).findFirst().orElseThrow();
        assertThat(props(hostCompleted).get("liked_count").asInt()).isEqualTo(2);
        assertThat(props(guestCompleted).get("liked_count").asInt()).isEqualTo(1);
        assertThat(props(hostCompleted).get("total_cards").asInt()).isEqualTo(CARDS);
        assertThat(props(hostCompleted).get("duration_seconds").asLong()).isGreaterThanOrEqualTo(0);

        List<AnalyticsEvent> won = backend(game, "movie_won");
        assertThat(won).hasSize(1);
        AnalyticsEvent winner = won.getFirst();
        assertThat(winner.getParticipantId()).isNull();
        assertThat(winner.getGameSessionId()).isEqualTo(game.gameSessionId());
        JsonNode properties = props(winner);
        assertThat(properties.get("movie_id").asLong()).isEqualTo(game.tmdbIds().get(0));
        assertThat(properties.get("title").asText()).isNotBlank();
        assertThat(properties.get("genres").isArray()).isTrue();
        assertThat(properties.get("release_year").isNumber()).isTrue();
        assertThat(properties.get("likes").asInt()).isEqualTo(2);
        assertThat(properties.get("participants").asInt()).isEqualTo(2);
        assertThat(properties.get("winners_count").asInt()).isEqualTo(1);
    }

    @Test
    void aTieRecordsOneMovieWonEventPerWinner() throws Exception {
        Game game = play(Set.of(0, 1), Set.of(0, 1));

        List<AnalyticsEvent> won = backend(game, "movie_won");

        assertThat(won).hasSize(2);
        assertThat(won).extracting(event -> props(event).get("movie_id").asLong())
                .containsExactlyInAnyOrder(game.tmdbIds().get(0), game.tmdbIds().get(1));
        assertThat(won).allSatisfy(event -> {
            assertThat(props(event).get("winners_count").asInt()).isEqualTo(2);
            assertThat(props(event).get("likes").asInt()).isEqualTo(2);
        });
    }

    @Test
    void whenNobodyLikesAnythingThereIsNoWinnerEvent() throws Exception {
        Game game = play(Set.of(), Set.of());

        assertThat(backend(game, "movie_won")).isEmpty();
        assertThat(backend(game, "swipe_session_started")).hasSize(2);
        assertThat(backend(game, "swipe_session_completed")).hasSize(2);
        assertThat(props(backend(game, "swipe_session_completed").getFirst()).get("liked_count").asInt()).isZero();
    }

    @Test
    void repeatedOrRejectedVotesRecordNothingMore() throws Exception {
        Game game = play(Set.of(0, 1), Set.of(0));
        long before = events.count();

        // the same vote again (the guest's last card was not liked) is accepted but is not progress
        voteRaw(game.code(), game.hostToken(), game.cardIds().get(0), true).andExpect(status().isOk());
        voteRaw(game.code(), game.guestToken(), game.cardIds().get(CARDS - 1), false).andExpect(status().isOk());
        // changing a saved vote and a card from another deck are errors
        voteRaw(game.code(), game.hostToken(), game.cardIds().get(0), false).andExpect(status().isConflict());
        voteRaw(game.code(), game.hostToken(), 999_999_999L, true).andExpect(status().isBadRequest());

        assertThat(events.count()).isEqualTo(before);
        assertThat(backend(game, "swipe_session_completed")).hasSize(2);
        assertThat(backend(game, "movie_won")).hasSize(1);
    }

    @Test
    void everyStartedGameGetsItsOwnSessionId() throws Exception {
        Game first = play(Set.of(0), Set.of(0));
        Game second = play(Set.of(0), Set.of(0));

        assertThat(first.gameSessionId()).isNotBlank();
        assertThat(second.gameSessionId()).isNotBlank().isNotEqualTo(first.gameSessionId());
    }

    @Test
    void frontendEventsGetTheGameSessionFromTheParticipantToken() throws Exception {
        Game game = play(Set.of(0), Set.of(0));
        String tab = UUID.randomUUID().toString();

        mvc.perform(post("/event").header(TOKEN, game.guestToken()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"winner_details_clicked\",\"occurredAt\":\"" + Instant.now()
                                + "\",\"sessionId\":\"" + tab + "\"}"))
                .andExpect(status().isAccepted());

        AnalyticsEvent stored = events.findAll().stream()
                .filter(event -> tab.equals(event.getClientSessionId())).findFirst().orElseThrow();
        assertThat(stored.getSource()).isEqualTo(EventSource.FRONTEND);
        assertThat(stored.getGameSessionId()).isEqualTo(game.gameSessionId());
        assertThat(stored.getRoomId()).isEqualTo(game.roomId());
        assertThat(stored.getParticipantId()).isEqualTo(game.guestParticipantId());
    }
}
