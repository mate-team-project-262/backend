package com.team262.moviematch.service.analytics;

import com.team262.moviematch.model.analytics.AnalyticsEvent;
import com.team262.moviematch.repository.AnalyticsEventRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The only place that writes events: one short transaction per call. */
@Service
@RequiredArgsConstructor
public class AnalyticsEventWriter {

    private final AnalyticsEventRepository repository;

    /** All events of a batch in one transaction; a unique-constraint error surfaces to the caller. */
    @Transactional
    public void saveAll(List<AnalyticsEvent> events) {
        repository.saveAll(events);
    }

    @Transactional
    public int purgeOlderThan(Instant cutoff) {
        return repository.deleteOlderThan(cutoff);
    }
}
