package com.team262.moviematch.service.history;

import com.team262.moviematch.dto.account.UserResponse;
import com.team262.moviematch.dto.history.ProfileResponse;
import com.team262.moviematch.dto.history.ProfileResponse.GenreStat;
import com.team262.moviematch.model.User;
import com.team262.moviematch.service.history.ProfileStatsCalculator.ActorRow;
import com.team262.moviematch.service.history.ProfileStatsCalculator.GenreRow;
import com.team262.moviematch.service.history.ProfileStatsCalculator.YearRow;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileService {

    /** One row per reaction of the user, joined to the archived card. All statistics start from this. */
    private static final String REACTIONS = """
            FROM archived_votes vote
            JOIN archived_participants member ON member.session_id = vote.session_id
                AND member.source_participant_id = vote.source_participant_id
            JOIN archived_cards card ON card.session_id = vote.session_id
                AND card.source_card_id = vote.source_card_id
            """;

    private final JdbcTemplate jdbc;
    private final ProfileStatsCalculator stats;

    public ProfileResponse profile(User user) {
        Long sessions = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT session_id) FROM archived_participants WHERE user_id = ?",
                Long.class, user.getId());
        long[] counts = jdbc.queryForObject("""
                SELECT COUNT(*), COALESCE(SUM(CASE WHEN vote.liked = TRUE THEN 1 ELSE 0 END), 0)
                FROM archived_votes vote JOIN archived_participants member
                ON member.session_id = vote.session_id
                AND member.source_participant_id = vote.source_participant_id
                WHERE member.user_id = ?
                """, (row, index) -> new long[]{row.getLong(1), row.getLong(2)}, user.getId());

        // PROFILE-STAT-1: one query gives likes and all reactions per genre
        // (genreMix, favoriteGenre and leastLikedGenre are all derived from it).
        List<GenreRow> genreRows = jdbc.query("""
                SELECT genre.genre_id, MAX(genre.genre_name),
                       SUM(CASE WHEN vote.liked = TRUE THEN 1 ELSE 0 END), COUNT(*)
                """ + REACTIONS + """
                JOIN title_genres genre ON genre.title_id = card.title_id
                WHERE member.user_id = ?
                GROUP BY genre.genre_id
                """, (row, index) -> new GenreRow(row.getInt(1), row.getString(2), row.getLong(3), row.getLong(4)),
                user.getId());

        // PROFILE-STAT-2: liked titles per release year (grouped into decades in Java,
        // which does not depend on the SQL dialect). Titles without a year are skipped.
        List<YearRow> yearRows = jdbc.query("""
                SELECT title.release_year, COUNT(*)
                """ + REACTIONS + """
                JOIN titles title ON title.id = card.title_id
                WHERE member.user_id = ? AND vote.liked = TRUE AND title.release_year IS NOT NULL
                GROUP BY title.release_year
                """, (row, index) -> new YearRow(row.getInt(1), row.getLong(2)), user.getId());

        // PROFILE-STAT-3: different liked titles per actor name.
        List<ActorRow> actorRows = jdbc.query("""
                SELECT actor.name, COUNT(DISTINCT card.title_id)
                """ + REACTIONS + """
                JOIN title_cast actor ON actor.title_id = card.title_id
                WHERE member.user_id = ? AND vote.liked = TRUE
                GROUP BY actor.name
                """, (row, index) -> new ActorRow(row.getString(1), row.getLong(2)), user.getId());

        List<GenreStat> genreMix = stats.genreMix(genreRows);
        var decadeMix = stats.decadeMix(yearRows);
        return new ProfileResponse(UserResponse.from(user), sessions, counts[0], counts[1],
                genreMix, genreMix.isEmpty() ? null : genreMix.getFirst(),
                stats.leastLikedGenre(genreRows),
                decadeMix, decadeMix.isEmpty() ? null : decadeMix.getFirst(),
                stats.favoriteActors(actorRows));
    }
}