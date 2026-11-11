package com.team262.moviematch.service.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team262.moviematch.dto.history.ProfileResponse.ActorStat;
import com.team262.moviematch.dto.history.ProfileResponse.DecadeStat;
import com.team262.moviematch.dto.history.ProfileResponse.GenreDislikeStat;
import com.team262.moviematch.dto.history.ProfileResponse.GenreStat;
import com.team262.moviematch.service.history.ProfileStatsCalculator.ActorRow;
import com.team262.moviematch.service.history.ProfileStatsCalculator.GenreRow;
import com.team262.moviematch.service.history.ProfileStatsCalculator.YearRow;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProfileStatsCalculatorTest {

    // minGenreRatings = 5, actorsLimit = 5, minActorTitles = 2
    private final ProfileStatsCalculator calculator = new ProfileStatsCalculator(5, 5, 2);

    private static GenreRow genre(int id, String name, long likes, long rated) {
        return new GenreRow(id, name, likes, rated);
    }

    // ---- empty history ----

    @Test
    void emptyHistoryGivesEmptyListsAndNulls() {
        assertThat(calculator.genreMix(List.of())).isEmpty();
        assertThat(calculator.leastLikedGenre(List.of())).isNull();
        assertThat(calculator.decadeMix(List.of())).isEmpty();
        assertThat(calculator.favoriteActors(List.of())).isEmpty();
    }

    // ---- PROFILE-STAT-1 ----

    @Test
    void genreMixIsSortedAndSumsToOneHundredPercent() {
        List<GenreStat> mix = calculator.genreMix(List.of(
                genre(35, "Comedy", 3, 10), genre(28, "Action", 6, 10),
                genre(18, "Drama", 3, 10), genre(27, "Horror", 0, 8)));

        assertThat(mix).extracting(GenreStat::id).containsExactly(28, 18, 35);   // tie 3:3 -> lower id first
        assertThat(mix).extracting(GenreStat::percentage).containsExactly(50.0, 25.0, 25.0);
        assertThat(mix).extracting(GenreStat::likesCount).doesNotContain(0L);     // genres without likes are not in the mix
    }

    @Test
    void leastLikedGenreUsesShareOfDislikesNotTheirNumber() {
        // Action: 10 dislikes of 20 (50%), Horror: 3 dislikes of 5 (60%)
        GenreDislikeStat result = calculator.leastLikedGenre(List.of(
                genre(28, "Action", 10, 20), genre(27, "Horror", 2, 5)));

        assertThat(result.id()).isEqualTo(27);
        assertThat(result.dislikesCount()).isEqualTo(3);
        assertThat(result.ratedCount()).isEqualTo(5);
        assertThat(result.dislikePercentage()).isEqualTo(60.0);
    }

    @Test
    void genreWithTooFewReactionsCannotBeLeastLiked() {
        // seen once and skipped = 100% dislikes, but below the minimum of 5 reactions
        GenreDislikeStat result = calculator.leastLikedGenre(List.of(
                genre(27, "Horror", 0, 1), genre(28, "Action", 8, 10)));

        assertThat(result.id()).isEqualTo(28);   // the only genre that qualifies
    }

    @Test
    void noGenreQualifiesWhenAllAreBelowTheMinimumOrHaveNoDislikes() {
        assertThat(calculator.leastLikedGenre(List.of(genre(27, "Horror", 0, 4)))).isNull();
        assertThat(calculator.leastLikedGenre(List.of(genre(28, "Action", 10, 10)))).isNull();
    }

    @Test
    void leastLikedTiesAreResolvedByMoreDislikesThenLowerId() {
        // 50%: 5/10 vs 10/20 -> the larger sample (more dislikes) wins
        assertThat(calculator.leastLikedGenre(List.of(genre(1, "A", 5, 10), genre(2, "B", 10, 20))).id())
                .isEqualTo(2);
        // identical numbers -> lower id wins
        assertThat(calculator.leastLikedGenre(List.of(genre(9, "Z", 5, 10), genre(3, "C", 5, 10))).id())
                .isEqualTo(3);
    }

    // ---- PROFILE-STAT-2 ----

    @Test
    void yearsAreGroupedIntoDecades() {
        List<DecadeStat> mix = calculator.decadeMix(List.of(
                new YearRow(2024, 10), new YearRow(2021, 5), new YearRow(1997, 5), new YearRow(1990, 0)));

        assertThat(mix).extracting(DecadeStat::decade).containsExactly(2020, 1990);
        assertThat(mix.get(0).label()).isEqualTo("2020s");
        assertThat(mix.get(0).likedCount()).isEqualTo(15);
        assertThat(mix.get(0).percentage()).isEqualTo(75.0);
        assertThat(mix.get(1).percentage()).isEqualTo(25.0);
    }

    @Test
    void decadeTiesPreferTheMoreRecentDecade() {
        List<DecadeStat> mix = calculator.decadeMix(List.of(new YearRow(1985, 4), new YearRow(2015, 4)));

        assertThat(mix).extracting(DecadeStat::decade).containsExactly(2010, 1980);
    }

    @Test
    void invalidYearsAreIgnored() {
        assertThat(calculator.decadeMix(List.of(new YearRow(0, 3), new YearRow(-5, 2)))).isEmpty();
    }

    // ---- PROFILE-STAT-3 ----

    @Test
    void actorsNeedEnoughLikedTitlesAndAreSortedWithStableTies() {
        List<ActorStat> actors = calculator.favoriteActors(List.of(
                new ActorRow("Zendaya", 4), new ActorRow("Ryan Gosling", 5),
                new ActorRow("Anna Taylor-Joy", 4), new ActorRow("One Hit", 1), new ActorRow(" ", 9)));

        assertThat(actors).extracting(ActorStat::name)
                .containsExactly("Ryan Gosling", "Anna Taylor-Joy", "Zendaya");   // tie 4:4 -> name A-Z
        assertThat(actors).extracting(ActorStat::likedTitlesCount).containsExactly(5L, 4L, 4L);
    }

    @Test
    void actorsAreLimitedToTheTopFive() {
        List<ActorRow> rows = List.of(
                new ActorRow("A", 9), new ActorRow("B", 8), new ActorRow("C", 7), new ActorRow("D", 6),
                new ActorRow("E", 5), new ActorRow("F", 4), new ActorRow("G", 3));

        assertThat(calculator.favoriteActors(rows)).extracting(ActorStat::name)
                .containsExactly("A", "B", "C", "D", "E");
    }

    @Test
    void rejectsInvalidThresholds() {
        assertThatThrownBy(() -> new ProfileStatsCalculator(0, 5, 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProfileStatsCalculator(5, 0, 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProfileStatsCalculator(5, 5, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}