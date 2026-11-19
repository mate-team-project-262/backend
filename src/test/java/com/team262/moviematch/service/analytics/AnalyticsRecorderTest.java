package com.team262.moviematch.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team262.moviematch.model.ContentType;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.model.Room;
import com.team262.moviematch.model.RoomTitle;
import com.team262.moviematch.model.StoredGenre;
import com.team262.moviematch.model.Title;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class AnalyticsRecorderTest {

    private static final String GAME_SESSION = "11111111-2222-4333-8444-555555555555";

    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final AnalyticsRecorder recorder = new AnalyticsRecorder(publisher, mapper);

    private Room room;
    private Participant participant;

    @BeforeEach
    void setUp() {
        room = new Room("K7M2QX");
        ReflectionTestUtils.setField(room, "id", 5L);
        room.setGameSessionId(GAME_SESSION);
        participant = new Participant(room, "Anna", true, "secret-token", LocalDateTime.now());
        ReflectionTestUtils.setField(participant, "id", 9L);
    }

    private List<BackendAnalyticsEvent> published() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(publisher, atLeastOnce()).publishEvent(captor.capture());
        return captor.getAllValues().stream().map(BackendAnalyticsEvent.class::cast).toList();
    }

    private JsonNode properties(BackendAnalyticsEvent event) throws Exception {
        return mapper.readTree(event.propertiesJson());
    }

    private RoomTitle card(long id, int position, long tmdbId, String name, int year) {
        Title title = new Title(tmdbId, ContentType.MOVIES, name);
        title.setReleaseYear(year);
        title.getGenres().add(new StoredGenre(878, "Science Fiction"));
        title.getGenres().add(new StoredGenre(12, "Adventure"));
        RoomTitle card = new RoomTitle(room, title, position);
        ReflectionTestUtils.setField(card, "id", id);
        return card;
    }

    @Test
    void swipeSessionStartedCarriesTheRoomSessionAndParticipant() throws Exception {
        recorder.swipeSessionStarted(room, participant, 20);

        BackendAnalyticsEvent event = published().getFirst();
        assertThat(event.name()).isEqualTo("swipe_session_started");
        assertThat(event.roomId()).isEqualTo(5L);
        assertThat(event.gameSessionId()).isEqualTo(GAME_SESSION);
        assertThat(event.participantId()).isEqualTo(9L);
        assertThat(event.userId()).isNull();                       // a guest
        assertThat(event.occurredAt()).isNotNull();
        assertThat(properties(event).get("total_cards").asInt()).isEqualTo(20);
    }

    @Test
    void swipeSessionCompletedHasLikedCountAndDuration() throws Exception {
        recorder.swipeSessionCompleted(room, participant, 20, 7, 95L);

        BackendAnalyticsEvent event = published().getFirst();
        assertThat(event.name()).isEqualTo("swipe_session_completed");
        JsonNode properties = properties(event);
        assertThat(properties.get("total_cards").asInt()).isEqualTo(20);
        assertThat(properties.get("liked_count").asInt()).isEqualTo(7);
        assertThat(properties.get("duration_seconds").asLong()).isEqualTo(95);
    }

    @Test
    void unknownDurationIsLeftOut() throws Exception {
        recorder.swipeSessionCompleted(room, participant, 20, 7, null);

        assertThat(properties(published().getFirst()).has("duration_seconds")).isFalse();
    }

    @Test
    void movieWonDescribesTheWinnerWithoutAParticipant() throws Exception {
        recorder.movieWon(room, List.of(card(41, 6, 438631, "Dune", 2021)), 4, 4);

        BackendAnalyticsEvent event = published().getFirst();
        assertThat(event.name()).isEqualTo("movie_won");
        assertThat(event.participantId()).isNull();
        assertThat(event.userId()).isNull();
        assertThat(event.roomId()).isEqualTo(5L);
        assertThat(event.gameSessionId()).isEqualTo(GAME_SESSION);
        JsonNode properties = properties(event);
        assertThat(properties.get("movie_id").asLong()).isEqualTo(438631);
        assertThat(properties.get("title").asText()).isEqualTo("Dune");
        assertThat(properties.get("release_year").asInt()).isEqualTo(2021);
        assertThat(properties.get("likes").asInt()).isEqualTo(4);
        assertThat(properties.get("participants").asInt()).isEqualTo(4);
        assertThat(properties.get("winners_count").asInt()).isEqualTo(1);
        assertThat(properties.get("genres")).extracting(JsonNode::asText)
                .containsExactly("Adventure", "Science Fiction");          // sorted by genre id
    }

    @Test
    void aTieGivesOneEventPerWinner() throws Exception {
        recorder.movieWon(room, List.of(card(41, 0, 100, "A", 2000), card(42, 1, 200, "B", 2001)), 3, 3);

        List<BackendAnalyticsEvent> events = published();
        assertThat(events).hasSize(2);
        assertThat(events).extracting(event -> properties(event).get("movie_id").asLong())
                .containsExactly(100L, 200L);
        assertThat(events).allSatisfy(event ->
                assertThat(properties(event).get("winners_count").asInt()).isEqualTo(2));
    }

    @Test
    void noWinnersPublishNothing() {
        recorder.movieWon(room, List.of(), 0, 3);

        verifyNoInteractions(publisher);
    }

    @Test
    void aFailureWhilePublishingNeverReachesTheGame() {
        doThrow(new IllegalStateException("broker down")).when(publisher).publishEvent(any(Object.class));

        assertThatCode(() -> recorder.swipeSessionStarted(room, participant, 20)).doesNotThrowAnyException();
        assertThatCode(() -> recorder.movieWon(room, List.of(card(41, 0, 100, "A", 2000)), 2, 2))
                .doesNotThrowAnyException();
    }

    @Test
    void everyRecordedNameIsReservedForTheServer() {
        assertThat(List.of(AnalyticsRecorder.SWIPE_SESSION_STARTED, AnalyticsRecorder.SWIPE_SESSION_COMPLETED,
                AnalyticsRecorder.MOVIE_WON)).allMatch(AnalyticsEventCatalog::isBackendOnly);
    }
}
