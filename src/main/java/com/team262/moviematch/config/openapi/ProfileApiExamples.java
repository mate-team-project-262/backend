package com.team262.moviematch.config.openapi;

public final class ProfileApiExamples {

    private ProfileApiExamples() {
    }

    public static final String AUTHENTICATION_REQUIRED = """
        {"code": "AUTHENTICATION_REQUIRED", "message": "Authentication required", "fieldErrors": []}
        """;

    public static final String HISTORY_NOT_FOUND = """
        {"code": "HISTORY_NOT_FOUND", "message": "Session not found", "fieldErrors": []}
        """;

    public static final String PROFILE_RESPONSE = """
            {
              "user": {"id": 7, "email": "egor@example.com", "displayName": "Egor"},
              "sessionsCount": 8,
              "ratedCount": 160,
              "likedCount": 47,
              "genreMix": [
                {"id": 28, "name": "Action", "likesCount": 18, "percentage": 41.9},
                {"id": 878, "name": "Science Fiction", "likesCount": 14, "percentage": 32.6},
                {"id": 18, "name": "Drama", "likesCount": 11, "percentage": 25.6}
              ],
              "favoriteGenre": {"id": 28, "name": "Action", "likesCount": 18, "percentage": 41.9},
              "leastLikedGenre": {"id": 27, "name": "Horror", "dislikesCount": 12, "ratedCount": 20,
                                  "dislikePercentage": 60.0},
              "decadeMix": [
                {"decade": 2020, "label": "2020s", "likedCount": 25, "percentage": 53.2},
                {"decade": 2010, "label": "2010s", "likedCount": 14, "percentage": 29.8},
                {"decade": 1990, "label": "1990s", "likedCount": 8, "percentage": 17.0}
              ],
              "favoriteDecade": {"decade": 2020, "label": "2020s", "likedCount": 25, "percentage": 53.2},
              "favoriteActors": [
                {"name": "Ryan Gosling", "likedTitlesCount": 5},
                {"name": "Zendaya", "likedTitlesCount": 4}
              ]
            }
            """;

    public static final String EMPTY_PROFILE_RESPONSE = """
            {
              "user": {"id": 7, "email": "egor@example.com", "displayName": "Egor"},
              "sessionsCount": 0, "ratedCount": 0, "likedCount": 0,
              "genreMix": [], "favoriteGenre": null, "leastLikedGenre": null,
              "decadeMix": [], "favoriteDecade": null, "favoriteActors": []
            }
            """;
}