package com.team262.moviematch.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team262.moviematch.model.analytics.AnalyticsEvent;
import com.team262.moviematch.model.analytics.EventSource;
import com.team262.moviematch.repository.AnalyticsEventRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

@SpringBootTest(properties = {"title.source=stub",
        "spring.liquibase.change-log=classpath:db/changelog/account-h2.yaml",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.datasource.url=jdbc:h2:mem:accounts;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password="})
class AnalyticsEventStorageIntegrationTest {

    @Autowired AnalyticsEventRepository events;
    @Autowired AnalyticsEventWriter writer;

    @BeforeEach
    void clean() {
        events.deleteAllInBatch();
    }

    private AnalyticsEvent event(String eventId, Instant receivedAt) {
        return new AnalyticsEvent(eventId, "settings_clicked", EventSource.FRONTEND, receivedAt, receivedAt,
                UUID.randomUUID().toString(), "/settings", null, null, null, null);
    }

    @Test
    void storesEveryFieldOfAnEvent() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String id = UUID.randomUUID().toString();
        writer.saveAll(List.of(new AnalyticsEvent(id, "genres_selected", EventSource.FRONTEND, now.minusSeconds(5),
                now, "tab-session", "/create", 7L, 11L, 13L, "{\"genres\":[28,35]}")));

        AnalyticsEvent stored = events.findAll().getFirst();

        assertThat(stored.getEventId()).isEqualTo(id);
        assertThat(stored.getName()).isEqualTo("genres_selected");
        assertThat(stored.getSource()).isEqualTo(EventSource.FRONTEND);
        assertThat(stored.getOccurredAt()).isEqualTo(now.minusSeconds(5));
        assertThat(stored.getReceivedAt()).isEqualTo(now);
        assertThat(stored.getClientSessionId()).isEqualTo("tab-session");
        assertThat(stored.getPage()).isEqualTo("/create");
        assertThat(stored.getUserId()).isEqualTo(7L);
        assertThat(stored.getParticipantId()).isEqualTo(11L);
        assertThat(stored.getRoomId()).isEqualTo(13L);
        assertThat(stored.getPropertiesJson()).isEqualTo("{\"genres\":[28,35]}");
    }

    @Test
    void eventsWithoutAnEventIdAreAllStored() {
        writer.saveAll(List.of(event(null, Instant.now()), event(null, Instant.now())));

        assertThat(events.count()).isEqualTo(2);
    }

    @Test
    void theSameEventIdCannotBeStoredTwice() {
        String id = UUID.randomUUID().toString();
        writer.saveAll(List.of(event(id, Instant.now())));

        assertThatThrownBy(() -> writer.saveAll(List.of(event(id, Instant.now()))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(events.count()).isEqualTo(1);
    }

    @Test
    void findsOnlyTheEventIdsThatExist() {
        String stored = UUID.randomUUID().toString();
        writer.saveAll(List.of(event(stored, Instant.now())));

        assertThat(events.findExistingEventIds(List.of(stored, UUID.randomUUID().toString())))
                .containsExactly(stored);
    }

    @Test
    void purgeDeletesOnlyEventsOlderThanTheCutoff() {
        Instant now = Instant.now();
        writer.saveAll(List.of(
                event(null, now.minus(500, ChronoUnit.DAYS)),
                event(null, now.minus(401, ChronoUnit.DAYS)),
                event(null, now.minus(399, ChronoUnit.DAYS)),
                event(null, now)));

        int deleted = writer.purgeOlderThan(now.minus(400, ChronoUnit.DAYS));

        assertThat(deleted).isEqualTo(2);
        assertThat(events.count()).isEqualTo(2);
    }
}
