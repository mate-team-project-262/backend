package com.team262.moviematch.repository;

import java.sql.DriverManager;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Disabled
class ContentTypeMigrationTest {
    @Test
    void convertsExistingTypesWithoutChangingTitleIdsOrRoomDeckLinksAndCanRollBack() throws Exception {
        try (var mysql = new MySQLContainer<>("mysql:8.0.44")) {
            mysql.start();
            try (var connection = DriverManager.getConnection(mysql.getJdbcUrl(),
                    mysql.getUsername(), mysql.getPassword());
                    var resources = new ClassLoaderResourceAccessor();
                    var liquibase = new Liquibase("db/changelog/db.changelog-master.yaml", resources,
                            DatabaseFactory.getInstance().findCorrectDatabaseImplementation(
                                    new JdbcConnection(connection)))) {
                var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
                liquibase.update(5, new Contexts(), new LabelExpression());
                jdbc.update("""
                        INSERT INTO titles (id, tmdb_id, media_type, title)
                        VALUES (1, 123, 'MOVIE', 'Movie'), (2, 123, 'TV', 'Series')
                        """);
                jdbc.update("INSERT INTO rooms (id, code) VALUES (1, 'ABC234')");
                jdbc.update("""
                        INSERT INTO room_titles (id, room_id, title_id, position)
                        VALUES (41, 1, 1, 0), (42, 1, 2, 1)
                        """);
                jdbc.update("INSERT INTO title_genres (title_id, genre_id, genre_name) VALUES (1, 18, 'Drama')");
                connection.commit();
                var identities = jdbc.queryForList("SELECT id, tmdb_id, title FROM titles ORDER BY id");
                var deck = jdbc.queryForList("SELECT * FROM room_titles ORDER BY position");
                liquibase.update(new Contexts(), new LabelExpression());

                assertThat(jdbc.queryForList("SELECT media_type FROM titles ORDER BY id", String.class))
                        .containsExactly("MOVIES", "TV_SERIES");
                assertThat(jdbc.queryForList("SELECT id, tmdb_id, title FROM titles ORDER BY id"))
                        .isEqualTo(identities);
                assertThat(jdbc.queryForList("SELECT * FROM room_titles ORDER BY position")).isEqualTo(deck);
                assertThat(jdbc.queryForObject("SELECT genre_name FROM title_genres WHERE title_id = 1",
                        String.class)).isEqualTo("Drama");
                assertThatThrownBy(() -> jdbc.update("""
                        INSERT INTO titles (tmdb_id, media_type, title) VALUES (999, 'MOVIE', 'Old value')
                        """)).rootCause().isInstanceOfSatisfying(java.sql.SQLException.class,
                                error -> assertThat(error.getErrorCode()).isEqualTo(3819));
                assertThatThrownBy(() -> jdbc.update("""
                        INSERT INTO titles (tmdb_id, media_type, title) VALUES (123, 'MOVIES', 'Duplicate')
                        """)).isInstanceOf(DataIntegrityViolationException.class);
                connection.commit();

                liquibase.rollback(1, new Contexts(), new LabelExpression());
                connection.commit();
                assertThat(jdbc.queryForList("SELECT media_type FROM titles ORDER BY id", String.class))
                        .containsExactly("MOVIES", "TV_SERIES");
                assertThat(jdbc.queryForList("SELECT * FROM room_titles ORDER BY position")).isEqualTo(deck);
                assertThat(jdbc.queryForObject("SELECT genre_name FROM title_genres WHERE title_id = 1",
                        String.class)).isEqualTo("Drama");
            }
        }
    }
}
