package com.team262.moviematch.repository;

import com.team262.moviematch.MySqlTestConfiguration;
import com.team262.moviematch.model.MediaType;
import com.team262.moviematch.model.Room;
import com.team262.moviematch.model.RoomTitle;
import com.team262.moviematch.model.Title;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.check_nullability=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MySqlTestConfiguration.class)
class TitlePersistenceTests {
    @Autowired
    private TitleRepository titles;
    @Autowired
    private RoomRepository rooms;
    @Autowired
    private RoomTitleRepository deck;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SpringLiquibase liquibase;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void migrationCreatesTablesAndCanRunAgain() throws Exception {
        assertThat(jdbc.queryForList("show tables", String.class))
                .contains("titles", "room_titles", "title_genres", "title_creators", "title_cast");
        Integer before = jdbc.queryForObject("select count(*) from DATABASECHANGELOG", Integer.class);
        assertThat(before).isEqualTo(3);
        liquibase.afterPropertiesSet();
        assertThat(jdbc.queryForObject("select count(*) from DATABASECHANGELOG", Integer.class))
                .isEqualTo(before);
    }

    @Test
    void allCardDetailsAndOrderedListsRoundTrip() {
        Title title = new Title(123L, MediaType.MOVIE, "Полное название 🎬");
        title.setReleaseYear(2026);
        title.setOverview("Подробное описание. ".repeat(500));
        title.setPosterPath("/poster.jpg");
        title.setPopularity(new BigDecimal("123456.123456"));
        title.setVoteAverage(new BigDecimal("8.75"));
        title.setRuntimeMinutes(142);
        title.getGenreIds().addAll(Set.of(18, 28));
        title.setDirector("Director");
        title.getCreators().addAll(List.of("Creator B", "Creator A"));
        title.getCast().addAll(List.of("Actor Z", "Actor A", "Actor Z"));
        title.setAgeRating("PG-13");
        title.setBudget(3000000000L);
        title.setRevenue(6000000000L);
        title.setOriginalLanguage("en");
        Instant created = Instant.parse("2026-10-31T10:20:30.123456Z");
        title.setCreatedAt(created);
        titles.saveAndFlush(title);
        entityManager.clear();

        Title loaded = titles.findByTmdbIdAndMediaType(123L, MediaType.MOVIE).orElseThrow();
        assertThat(loaded.getId()).isEqualTo(title.getId());
        assertThat(loaded.getTitle()).isEqualTo(title.getTitle());
        assertThat(loaded.getReleaseYear()).isEqualTo(2026);
        assertThat(loaded.getOverview()).isEqualTo(title.getOverview());
        assertThat(loaded.getPosterPath()).isEqualTo("/poster.jpg");
        assertThat(loaded.getPopularity()).isEqualByComparingTo("123456.123456");
        assertThat(loaded.getVoteAverage()).isEqualByComparingTo("8.75");
        assertThat(loaded.getRuntimeMinutes()).isEqualTo(142);
        assertThat(loaded.getGenreIds()).containsExactlyInAnyOrder(18, 28);
        assertThat(loaded.getDirector()).isEqualTo("Director");
        assertThat(loaded.getCreators()).containsExactly("Creator B", "Creator A");
        assertThat(loaded.getCast()).containsExactly("Actor Z", "Actor A", "Actor Z");
        assertThat(loaded.getAgeRating()).isEqualTo("PG-13");
        assertThat(loaded.getBudget()).isEqualTo(3000000000L);
        assertThat(loaded.getRevenue()).isEqualTo(6000000000L);
        assertThat(loaded.getOriginalLanguage()).isEqualTo("en");
        assertThat(loaded.getCreatedAt()).isEqualTo(created);
    }

    @Test
    void missingCatalogDetailsStayNullAndCollectionsStayEmpty() {
        Title title = titles.saveAndFlush(new Title(123L, MediaType.TV, "Series"));
        entityManager.clear();
        Title loaded = titles.findById(title.getId()).orElseThrow();
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getReleaseYear()).isNull();
        assertThat(loaded.getRuntimeMinutes()).isNull();
        assertThat(loaded.getBudget()).isNull();
        assertThat(loaded.getGenreIds()).isEmpty();
        assertThat(loaded.getCreators()).isEmpty();
        assertThat(loaded.getCast()).isEmpty();
    }

    @Test
    void duplicateCatalogIdentityIsRejected() {
        titles.saveAndFlush(new Title(123L, MediaType.MOVIE, "Original"));
        assertThatThrownBy(() -> titles.saveAndFlush(new Title(123L, MediaType.MOVIE, "Other")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameTmdbIdCanIdentifyMovieAndTvSeries() {
        Title movie = titles.saveAndFlush(new Title(123L, MediaType.MOVIE, "Movie"));
        Title series = titles.saveAndFlush(new Title(123L, MediaType.TV, "Series"));
        entityManager.clear();
        assertThat(titles.findByTmdbIdAndMediaType(123L, MediaType.MOVIE))
                .map(Title::getId).contains(movie.getId());
        assertThat(titles.findByTmdbIdAndMediaType(123L, MediaType.TV))
                .map(Title::getId).contains(series.getId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void committedDeckSurvivesNewTransactionsAndReusesTitlesAcrossRooms() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Long[] ids = tx.execute(status -> {
            Room first = rooms.save(new Room("ABC234"));
            Room second = rooms.save(new Room("DEF567"));
            Title one = titles.save(new Title(987L, MediaType.MOVIE, "One"));
            one.setOverview("Persisted details");
            one.getCast().add("Actor");
            Title two = titles.save(new Title(988L, MediaType.TV, "Two"));
            deck.save(new RoomTitle(first, two, 1));
            deck.save(new RoomTitle(first, one, 0));
            Title reused = titles.findByTmdbIdAndMediaType(987L, MediaType.MOVIE).orElseThrow();
            deck.save(new RoomTitle(second, reused, 0));
            return new Long[]{first.getId(), second.getId(), one.getId(), two.getId()};
        });
        try {
            for (int reader = 0; reader < 2; reader++) {
                tx.executeWithoutResult(status -> {
                    List<RoomTitle> loaded = deck.findAllByRoomIdOrderByPositionAsc(ids[0]);
                    assertThat(loaded).extracting(RoomTitle::getPosition).containsExactly(0, 1);
                    assertThat(loaded).extracting(card -> card.getTitle().getId())
                            .containsExactly(ids[2], ids[3]);
                    assertThat(loaded.getFirst().getTitle().getOverview())
                            .isEqualTo("Persisted details");
                    assertThat(loaded.getFirst().getTitle().getCast()).containsExactly("Actor");
                    assertThat(deck.findAllByRoomIdOrderByPositionAsc(ids[1]))
                            .extracting(card -> card.getTitle().getId()).containsExactly(ids[2]);
                    assertThat(titles.findAll()).filteredOn(t -> t.getTmdbId().equals(987L))
                            .hasSize(1);
                });
            }
        } finally {
            tx.executeWithoutResult(status -> {
                rooms.deleteAllById(List.of(ids[0], ids[1]));
                rooms.flush();
                titles.deleteAllById(List.of(ids[2], ids[3]));
                titles.flush();
            });
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 20})
    void positionsOutsideDeckRangeAreRejectedByMysql(int position) {
        Room room = rooms.save(new Room("ABC234"));
        Title title = titles.save(new Title(123L, MediaType.MOVIE, "Movie"));
        assertMysqlError(3819, () -> deck.saveAndFlush(new RoomTitle(room, title, position)));
    }

    @Test
    void twentyCardsAreReturnedInStoredOrderIncludingBoundaryPositions() {
        Room room = rooms.save(new Room("ABC234"));
        for (int position = 19; position >= 0; position--) {
            Title title = titles.save(new Title((long) position, MediaType.MOVIE, "Movie " + position));
            deck.save(new RoomTitle(room, title, position));
        }
        deck.flush();
        entityManager.clear();
        assertThat(deck.findAllByRoomIdOrderByPositionAsc(room.getId()))
                .extracting(RoomTitle::getPosition)
                .containsExactlyElementsOf(java.util.stream.IntStream.range(0, 20).boxed().toList());
        assertThat(deck.findAllByRoomIdOrderByPositionAsc(Long.MAX_VALUE)).isEmpty();
    }

    @Test
    void twoTitlesCannotOccupySamePositionInRoom() {
        Room room = rooms.save(new Room("ABC234"));
        Title first = titles.save(new Title(123L, MediaType.MOVIE, "One"));
        Title second = titles.save(new Title(124L, MediaType.MOVIE, "Two"));
        deck.saveAndFlush(new RoomTitle(room, first, 0));
        assertThatThrownBy(() -> deck.saveAndFlush(new RoomTitle(room, second, 0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameTitleCannotAppearTwiceInRoom() {
        Room room = rooms.save(new Room("ABC234"));
        Title title = titles.save(new Title(123L, MediaType.MOVIE, "One"));
        deck.saveAndFlush(new RoomTitle(room, title, 0));
        assertThatThrownBy(() -> deck.saveAndFlush(new RoomTitle(room, title, 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingRoomRemovesOnlyItsDeckAndKeepsSharedTitle() {
        Room first = rooms.save(new Room("ABC234"));
        Room second = rooms.save(new Room("DEF567"));
        Title title = titles.save(new Title(123L, MediaType.MOVIE, "One"));
        title.getGenreIds().add(28);
        title.getCreators().add("Creator");
        title.getCast().add("Actor");
        deck.save(new RoomTitle(first, title, 0));
        deck.saveAndFlush(new RoomTitle(second, title, 0));
        jdbc.update("delete from rooms where id = ?", first.getId());
        entityManager.clear();
        assertThat(deck.findAllByRoomIdOrderByPositionAsc(first.getId())).isEmpty();
        assertThat(deck.findAllByRoomIdOrderByPositionAsc(second.getId())).hasSize(1);
        assertThat(titles.findById(title.getId())).isPresent();
        assertThat(jdbc.queryForObject("select count(*) from title_genres where title_id = ?",
                Integer.class, title.getId())).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("delete from titles where id = ?", title.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingUnreferencedTitleCleansItsCollections() {
        Title title = new Title(123L, MediaType.TV, "Series");
        title.getGenreIds().add(18);
        title.getCreators().add("Creator");
        title.getCast().add("Actor");
        titles.saveAndFlush(title);
        jdbc.update("delete from titles where id = ?", title.getId());
        for (String table : List.of("title_genres", "title_creators", "title_cast")) {
            assertThat(jdbc.queryForObject("select count(*) from " + table + " where title_id = ?",
                    Integer.class, title.getId())).isZero();
        }
    }

    @Test
    void databaseEnforcesForeignKeysAndMandatoryFields() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into room_titles (room_id, title_id, position) values (?, ?, ?)",
                Long.MAX_VALUE, Long.MAX_VALUE, 0)).isInstanceOf(DataIntegrityViolationException.class);
        assertMysqlError(3819, () -> jdbc.update(
                "insert into titles (tmdb_id, media_type, title) values (123, 'OTHER', 'Name')"));
        assertMysqlError(1364, () -> jdbc.update(
                "insert into titles (tmdb_id, media_type) values (123, 'MOVIE')"));
    }

    private static void assertMysqlError(int expectedCode, Executable operation) {
        RuntimeException exception = assertThrows(RuntimeException.class, operation);
        Throwable cause = exception;
        while (!(cause instanceof SQLException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        assertThat(cause).isInstanceOf(SQLException.class);
        assertThat(((SQLException) cause).getErrorCode()).isEqualTo(expectedCode);
    }
}
