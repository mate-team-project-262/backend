package com.team262.moviematch.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.MySQLContainer;

class RoundResultsMigrationTest {
    @Test
    void snapshotsCompletedRoundsAndLeavesIncompleteRoundsOpen() throws Exception {
        try (var mysql = new MySQLContainer<>("mysql:8.0.44")) {
            mysql.start();
            try (var connection = DriverManager.getConnection(mysql.getJdbcUrl(),
                    mysql.getUsername(), mysql.getPassword());
                    var resources = new ClassLoaderResourceAccessor();
                    var liquibase = new Liquibase("db/changelog/db.changelog-master.yaml", resources,
                            DatabaseFactory.getInstance().findCorrectDatabaseImplementation(
                                    new JdbcConnection(connection)))) {
                var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
                var contexts = new Contexts();
                var labels = new LabelExpression();
                liquibase.update(8, contexts, labels);
                jdbc.update("""
                        INSERT INTO rooms (id, code, status)
                        VALUES (1, 'ABC234', 'VOTING'), (2, 'ABC235', 'VOTING')
                        """);
                jdbc.update("""
                        INSERT INTO participants (id, room_id, name, token, token_created_at)
                        VALUES (1, 1, 'A', UUID(), NOW()), (2, 1, 'B', UUID(), NOW()), (3, 2, 'C', UUID(), NOW())
                        """);
                jdbc.update("""
                        INSERT INTO titles (id, tmdb_id, media_type, title)
                        VALUES (1, 123, 'MOVIES', 'First'), (2, 124, 'MOVIES', 'Second')
                        """);
                jdbc.update("""
                        INSERT INTO room_titles (id, room_id, title_id, position)
                        VALUES (1, 1, 1, 0), (2, 1, 2, 1), (3, 2, 1, 0), (4, 2, 2, 1)
                        """);
                jdbc.update("""
                        INSERT INTO votes (participant_id, room_title_id, is_liked)
                        VALUES (1, 1, TRUE), (1, 2, FALSE), (2, 1, TRUE), (2, 2, FALSE), (3, 3, TRUE)
                        """);
                connection.commit();

                liquibase.update(contexts, labels);

                assertThat(jdbc.queryForList("SELECT status FROM rooms ORDER BY id", String.class))
                        .containsExactly("FINISHED", "VOTING");
                assertThat(jdbc.queryForList("SELECT result_participant_count FROM rooms ORDER BY id", Integer.class))
                        .containsExactly(2, null);
                assertThat(jdbc.queryForList(
                        "SELECT result_likes_count FROM room_titles ORDER BY id", Integer.class))
                        .containsExactly(2, 0, 0, 0);
            }
        }
    }
}
