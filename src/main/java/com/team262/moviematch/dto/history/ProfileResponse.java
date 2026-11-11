package com.team262.moviematch.dto.history;

import com.team262.moviematch.dto.account.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ProfileResponse(
        UserResponse user,
        long sessionsCount,
        long ratedCount,
        long likedCount,
        @Schema(description = "Liked genres. percentage = share of all genre-likes, so the list sums to 100")
        List<GenreStat> genreMix,
        @Schema(description = "Most liked genre; null when there are no likes", nullable = true)
        GenreStat favoriteGenre,
        @Schema(description = "Genre with the highest share of dislikes among genres with enough ratings; "
                + "null when no genre qualifies", nullable = true)
        GenreDislikeStat leastLikedGenre,
        @Schema(description = "Liked titles per decade; percentage = share of likes with a known release year")
        List<DecadeStat> decadeMix,
        @Schema(description = "Decade with the most likes; null when no liked title has a release year", nullable = true)
        DecadeStat favoriteDecade,
        @Schema(description = "Actors from liked titles, most liked titles first; only actors with enough liked titles")
        List<ActorStat> favoriteActors) {

    public record GenreStat(int id, String name, long likesCount, double percentage) {
    }

    public record GenreDislikeStat(
            int id,
            String name,
            @Schema(description = "Skipped (disliked) reactions on titles of this genre") long dislikesCount,
            @Schema(description = "All reactions (likes + dislikes) on titles of this genre") long ratedCount,
            @Schema(description = "dislikesCount / ratedCount * 100, one decimal place") double dislikePercentage) {
    }

    public record DecadeStat(
            @Schema(description = "First year of the decade", example = "2020") int decade,
            @Schema(description = "Display label", example = "2020s") String label,
            long likedCount,
            double percentage) {
    }

    public record ActorStat(
            String name,
            @Schema(description = "Number of different titles with this actor that the user liked")
            long likedTitlesCount) {
    }
}