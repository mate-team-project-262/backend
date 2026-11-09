package com.team262.moviematch.dto.history;
import com.team262.moviematch.model.ContentType;
import java.time.Instant;
import java.util.List;
public final class HistoryDtos {
    private HistoryDtos() {}
    public record TitleDto(Long id, String title, Integer year, String posterUrl,
                           Integer runtimeMinutes, java.math.BigDecimal voteAverage) {}
    public record ParticipantDto(String displayName, boolean host, boolean me) {}
    public record CardDto(int position, TitleDto title, int likesCount, boolean winner, Boolean myVote) {}
    public record Summary(Long sessionId, Instant finishedAt, ContentType contentType,
                          int participantCount, List<TitleDto> winnerTitles, long ratedCount, long likedCount) {}
    public record Details(Summary session, List<ParticipantDto> participants, List<CardDto> cards) {}
    public record HistoryPage(List<Summary> items, int page, int size, long totalElements, int totalPages) {}
}

