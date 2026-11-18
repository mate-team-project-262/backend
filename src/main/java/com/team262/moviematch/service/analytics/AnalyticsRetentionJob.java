package com.team262.moviematch.service.analytics;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Deletes events older than analytics.retention-days (default 400 = 13 months). */
@Slf4j
@Component
@EnableScheduling
public class AnalyticsRetentionJob {

    private final AnalyticsEventWriter writer;
    private final int retentionDays;

    public AnalyticsRetentionJob(AnalyticsEventWriter writer,
                                 @Value("${analytics.retention-days:400}") int retentionDays) {
        if (retentionDays < 1) {
            throw new IllegalArgumentException("analytics.retention-days must be >= 1");
        }
        this.writer = writer;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${analytics.retention-cron:0 30 3 * * *}")
    public void purge() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        try {
            int deleted = writer.purgeOlderThan(cutoff);
            log.info("Analytics retention: deleted {} events older than {}", deleted, cutoff);
        } catch (RuntimeException exception) {
            log.error("Analytics retention failed", exception);     // retried the next night
        }
    }
}
