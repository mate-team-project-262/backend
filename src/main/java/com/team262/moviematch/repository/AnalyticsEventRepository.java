package com.team262.moviematch.repository;

import com.team262.moviematch.model.analytics.AnalyticsEvent;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, Long> {

    @Query("select e.eventId from AnalyticsEvent e where e.eventId in :ids")
    List<String> findExistingEventIds(@Param("ids") Collection<String> ids);

    @Modifying
    @Query("delete from AnalyticsEvent e where e.receivedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
