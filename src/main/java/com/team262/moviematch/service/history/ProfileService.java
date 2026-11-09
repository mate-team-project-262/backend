package com.team262.moviematch.service.history;
import com.team262.moviematch.dto.account.UserResponse;
import com.team262.moviematch.dto.history.ProfileResponse;
import com.team262.moviematch.dto.history.ProfileResponse.GenreStat;
import com.team262.moviematch.model.User;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileService {
    private final JdbcTemplate jdbc;
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
        List<GenreStat> genres = jdbc.query("""
                SELECT genre.genre_id, MAX(genre.genre_name), COUNT(*) AS likes
                FROM archived_votes vote
                JOIN archived_participants member ON member.session_id = vote.session_id
                    AND member.source_participant_id = vote.source_participant_id
                JOIN archived_cards card ON card.session_id = vote.session_id
                    AND card.source_card_id = vote.source_card_id
                JOIN title_genres genre ON genre.title_id = card.title_id
                WHERE member.user_id = ? AND vote.liked = TRUE
                GROUP BY genre.genre_id ORDER BY likes DESC, genre.genre_id ASC
                """, (row, index) -> new GenreStat(row.getInt(1), row.getString(2), row.getLong(3), 0),
                user.getId());
        long total = genres.stream().mapToLong(GenreStat::likesCount).sum();
        List<GenreStat> distribution = genres.stream().map(genre -> new GenreStat(
                genre.id(), genre.name(), genre.likesCount(),
                total == 0 ? 0 : Math.round(1000.0 * genre.likesCount() / total) / 10.0)).toList();
        return new ProfileResponse(UserResponse.from(user), sessions, counts[0], counts[1],
                distribution, distribution.isEmpty() ? null : distribution.getFirst());
    }
}

