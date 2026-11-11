package com.team262.moviematch.service.history;

import com.team262.moviematch.dto.history.ProfileResponse.ActorStat;
import com.team262.moviematch.dto.history.ProfileResponse.DecadeStat;
import com.team262.moviematch.dto.history.ProfileResponse.GenreDislikeStat;
import com.team262.moviematch.dto.history.ProfileResponse.GenreStat;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ProfileStatsCalculator {
    private final int minGenreRatings;
    private final int actorsLimit;
    private final int minActorTitles;

    /** Per genre: likes and all reactions (likes + dislikes) of one user. */
    public record GenreRow(int id, String name, long likes, long rated) {
    }

    /** Likes per release year. */
    public record YearRow(int year, long likes) {
    }

    /** Number of different liked titles per actor name. */
    public record ActorRow(String name, long likedTitles) {
    }

    public ProfileStatsCalculator(@Value("${profile.min-genre-ratings:5}") int minGenreRatings,
                                  @Value("${profile.actors-limit:5}") int actorsLimit,
                                  @Value("${profile.min-actor-titles:2}") int minActorTitles) {
        if (minGenreRatings < 1 || actorsLimit < 1 || minActorTitles < 1) {
            throw new IllegalArgumentException("profile.* thresholds must be >= 1");
        }
        this.minGenreRatings = minGenreRatings;
        this.actorsLimit = actorsLimit;
        this.minActorTitles = minActorTitles;
    }

    /** Liked genres, most likes first (ties: lower genre id first). Percentages sum to ~100. */
    public List<GenreStat> genreMix(List<GenreRow> rows) {
        List<GenreRow> liked = rows.stream()
                .filter(row -> row.likes() > 0)
                .sorted(Comparator.comparingLong(GenreRow::likes).reversed()
                        .thenComparingInt(GenreRow::id))
                .toList();
        long total = liked.stream().mapToLong(GenreRow::likes).sum();
        return liked.stream()
                .map(row -> new GenreStat(row.id(), row.name(), row.likes(), percent(row.likes(), total)))
                .toList();
    }

    /**
     * Highest share of dislikes (not the highest number of them), so a genre seen once cannot win:
     * a genre needs at least minGenreRatings reactions and at least one dislike.
     * Ties: more dislikes first, then lower genre id.
     */
    public GenreDislikeStat leastLikedGenre(List<GenreRow> rows) {
        return rows.stream()
                .filter(row -> row.rated() >= minGenreRatings)
                .filter(row -> row.rated() - row.likes() > 0)
                .min(Comparator
                        .comparingDouble((GenreRow row) -> (double) (row.rated() - row.likes()) / row.rated())
                        .reversed()
                        .thenComparing(Comparator.comparingLong((GenreRow row) -> row.rated() - row.likes()).reversed())
                        .thenComparingInt(GenreRow::id))
                .map(row -> new GenreDislikeStat(row.id(), row.name(), row.rated() - row.likes(),
                        row.rated(), percent(row.rated() - row.likes(), row.rated())))
                .orElse(null);
    }

    /** Groups years into decades (1997 -> 1990). Most likes first, ties: more recent decade first. */
    public List<DecadeStat> decadeMix(List<YearRow> rows) {
        Map<Integer, Long> byDecade = new TreeMap<>();
        for (YearRow row : rows) {
            if (row.year() > 0 && row.likes() > 0) {
                byDecade.merge(row.year() / 10 * 10, row.likes(), Long::sum);
            }
        }
        long total = byDecade.values().stream().mapToLong(Long::longValue).sum();
        return byDecade.entrySet().stream()
                .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.<Integer, Long>comparingByKey().reversed()))
                .map(entry -> new DecadeStat(entry.getKey(), entry.getKey() + "s",
                        entry.getValue(), percent(entry.getValue(), total)))
                .toList();
    }

    /** Actors with at least minActorTitles liked titles; most titles first, ties: name A-Z; top actorsLimit. */
    public List<ActorStat> favoriteActors(List<ActorRow> rows) {
        return rows.stream()
                .filter(row -> row.name() != null && !row.name().isBlank())
                .filter(row -> row.likedTitles() >= minActorTitles)
                .sorted(Comparator.comparingLong(ActorRow::likedTitles).reversed()
                        .thenComparing(ActorRow::name))
                .limit(actorsLimit)
                .map(row -> new ActorStat(row.name(), row.likedTitles()))
                .toList();
    }

    static double percent(long part, long total) {
        return total == 0 ? 0 : Math.round(1000.0 * part / total) / 10.0;
    }
}