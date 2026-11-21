package com.team262.moviematch.mapper;

import com.team262.moviematch.model.ContentType;
import com.team262.moviematch.model.Title;
import com.team262.moviematch.source.TitleGenre;
import com.team262.moviematch.source.CastPhoto;
import com.team262.moviematch.source.TmdbTitle;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class TitleMapperTest {

    private final TitleMapper mapper = new TitleMapperImpl();   // klasa generowana przez MapStruct

    private TmdbTitle sample(List<String> cast) {
        return new TmdbTitle(693134L, ContentType.MOVIES, "Dune: Part Two", 2024,
                "Overview", "/p.jpg", new BigDecimal("450.2"), new BigDecimal("8.3"), 167,
                List.of(new TitleGenre(878, "Science Fiction"), new TitleGenre(null, "bez id")),
                "Denis Villeneuve", List.of(), cast, "PG-13",
                190_000_000L, 714_444_358L, "en");
    }

    private Title newEntity(TmdbTitle s) {
        return new Title(s.tmdbId(), s.mediaType(), s.title());
    }

    @Test
    void copiesAllFields() {
        TmdbTitle source = sample(List.of("Zendaya", "Timothée Chalamet"));
        Title t = newEntity(source);

        mapper.update(source, t);

        assertThat(t.getTmdbId()).isEqualTo(693134L);
        assertThat(t.getMediaType()).isEqualTo(ContentType.MOVIES);
        assertThat(t.getTitle()).isEqualTo("Dune: Part Two");
        assertThat(t.getReleaseYear()).isEqualTo(2024);
        assertThat(t.getPosterPath()).isEqualTo("/p.jpg");
        assertThat(t.getPopularity()).isEqualByComparingTo("450.2");
        assertThat(t.getVoteAverage()).isEqualByComparingTo("8.3");
        assertThat(t.getRuntimeMinutes()).isEqualTo(167);
        assertThat(t.getDirector()).isEqualTo("Denis Villeneuve");
        assertThat(t.getAgeRating()).isEqualTo("PG-13");
        assertThat(t.getBudget()).isEqualTo(190_000_000L);
        assertThat(t.getRevenue()).isEqualTo(714_444_358L);
        assertThat(t.getOriginalLanguage()).isEqualTo("en");
    }

    @Test
    void preservesActorPhotoDataWithoutChangingCardCastNames() {
        TmdbTitle base = sample(List.of("Zendaya"));
        TmdbTitle source = new TmdbTitle(base.tmdbId(), base.mediaType(), base.title(),
                base.releaseYear(), base.overview(), base.posterPath(), base.popularity(),
                base.voteAverage(), base.runtimeMinutes(), base.genres(), base.director(),
                base.creators(), base.cast(), base.ageRating(), base.budget(), base.revenue(),
                base.originalLanguage(), List.of(new CastPhoto(505710L, "/zendaya.jpg")));
        Title title = newEntity(source);

        mapper.update(source, title);

        assertThat(title.getCast()).isEqualTo(List.of("Zendaya"));
        assertThat(title.getCastPhotos().getFirst().getPersonId()).isEqualTo(505710L);
        assertThat(title.getCastPhotos().getFirst().getProfilePath()).isEqualTo("/zendaya.jpg");
    }
}
