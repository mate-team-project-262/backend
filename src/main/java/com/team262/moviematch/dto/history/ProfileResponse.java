package com.team262.moviematch.dto.history;
import com.team262.moviematch.dto.account.UserResponse;
import java.util.List;
public record ProfileResponse(UserResponse user, long sessionsCount, long ratedCount, long likedCount,
                              List<GenreStat> genreMix, GenreStat favoriteGenre) {
    public record GenreStat(int id, String name, long likesCount, double percentage) {}
}

