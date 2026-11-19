package com.team262.moviematch.service.analytics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.model.Room;
import com.team262.moviematch.model.RoomTitle;
import com.team262.moviematch.model.StoredGenre;
import com.team262.moviematch.model.Title;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * What the game code calls to record backend events. It only PUBLISHES an application event;
 * AnalyticsBackendEventListener writes it after the game transaction commits. This class never throws:
 * analytics must not break voting.
 * Property keys are snake_case, like in the analytics specification.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyticsRecorder {

    static final String SWIPE_SESSION_STARTED = "swipe_session_started";
    static final String SWIPE_SESSION_COMPLETED = "swipe_session_completed";
    static final String MOVIE_WON = "movie_won";

    private final ApplicationEventPublisher publisher;
    private final ObjectMapper mapper;

    /** The participant saved the first vote of a game. */
    public void swipeSessionStarted(Room room, Participant participant, int totalCards) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("total_cards", totalCards);
        publish(SWIPE_SESSION_STARTED, room, participant, properties);
    }

    /** The participant saved the last vote of a game. durationSeconds may be null when unknown. */
    public void swipeSessionCompleted(Room room, Participant participant, int totalCards,
                                      long likedCount, Long durationSeconds) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("total_cards", totalCards);
        properties.put("liked_count", likedCount);
        if (durationSeconds != null) {
            properties.put("duration_seconds", durationSeconds);
        }
        publish(SWIPE_SESSION_COMPLETED, room, participant, properties);
    }

    /** One event per winning movie (a tie gives several events with the same winners_count). */
    public void movieWon(Room room, List<RoomTitle> winners, int likes, int participants) {
        for (RoomTitle card : winners) {
            Title title = card.getTitle();
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("movie_id", title.getTmdbId());
            properties.put("title", title.getTitle());
            properties.put("genres", title.getGenres().stream()
                    .sorted(Comparator.comparing(StoredGenre::getId))
                    .map(StoredGenre::getName)
                    .toList());
            properties.put("release_year", title.getReleaseYear());
            properties.put("likes", likes);
            properties.put("participants", participants);
            properties.put("winners_count", winners.size());
            publish(MOVIE_WON, room, null, properties);
        }
    }

    private void publish(String name, Room room, Participant participant, Map<String, Object> properties) {
        try {
            Long userId = participant == null || participant.getUser() == null
                    ? null : participant.getUser().getId();
            publisher.publishEvent(new BackendAnalyticsEvent(name, Instant.now(), room.getId(),
                    room.getGameSessionId(), participant == null ? null : participant.getId(), userId,
                    mapper.writeValueAsString(properties)));
        } catch (JsonProcessingException | RuntimeException exception) {
            log.warn("Could not record analytics event {}", name, exception);
        }
    }
}
