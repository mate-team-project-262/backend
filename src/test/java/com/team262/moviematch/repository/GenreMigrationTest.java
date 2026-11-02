package com.team262.moviematch.repository;

import java.sql.DriverManager;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.Scope;
import liquibase.changelog.ChangeLogHistoryServiceFactory;
import liquibase.changelog.FastCheckService;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

class GenreMigrationTest {
    @Test
    void upgradesExistingGenreIdsBackfillsKnownNamesAndSupportsRollback() throws Exception {
        try (var mysql = new MySQLContainer<>("mysql:8.0.44")) {
            mysql.start();
            try (var connection = DriverManager.getConnection(mysql.getJdbcUrl(),
                    mysql.getUsername(), mysql.getPassword());
                    var resources = new ClassLoaderResourceAccessor();
                    var liquibase = new Liquibase("db/changelog/db.changelog-master.yaml", resources,
                            DatabaseFactory.getInstance().findCorrectDatabaseImplementation(
                                    new JdbcConnection(connection)))) {
                var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
                liquibase.update(4, new Contexts(), new LabelExpression());
                jdbc.update("INSERT INTO titles (tmdb_id, media_type, title) VALUES (550, 'MOVIE', 'Movie')");
                jdbc.update("INSERT INTO titles (tmdb_id, media_type, title) VALUES (1396, 'TV', 'Series')");
                jdbc.update("""
                        INSERT INTO title_genres (title_id, genre_id)
                        SELECT id, 18 FROM titles WHERE tmdb_id = 550
                        UNION ALL SELECT id, 28 FROM titles WHERE tmdb_id = 550
                        UNION ALL SELECT id, 999999 FROM titles WHERE tmdb_id = 550
                        UNION ALL SELECT id, 10759 FROM titles WHERE tmdb_id = 1396
                        """);
                connection.commit();

                liquibase.update(new Contexts(), new LabelExpression());
                assertThat(jdbc.queryForObject("SELECT genre_name FROM title_genres WHERE genre_id = 18",
                        String.class)).isEqualTo("Drama");
                assertThat(jdbc.queryForObject("SELECT genre_name FROM title_genres WHERE genre_id = 10759",
                        String.class)).isEqualTo("Action & Adventure");
                assertThat(jdbc.queryForObject("SELECT genre_name FROM title_genres WHERE genre_id = 999999",
                        String.class)).isNull();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM title_genres", Integer.class)).isEqualTo(4);
                jdbc.update("UPDATE title_genres SET genre_name = 'Драма' WHERE genre_id = 18");
                connection.commit();
                liquibase.update(new Contexts(), new LabelExpression());
                assertThat(jdbc.queryForObject("SELECT genre_name FROM title_genres WHERE genre_id = 18",
                        String.class)).isEqualTo("Драма");

                liquibase.rollback(1, new Contexts(), new LabelExpression());
                connection.commit();
                assertThat(jdbc.queryForObject("""
                        SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = DATABASE() AND table_name = 'title_genres'
                        AND column_name = 'genre_name'
                        """, Integer.class)).isZero();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM title_genres", Integer.class)).isEqualTo(4);
                assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM DATABASECHANGELOG WHERE ID = '005-title-genre-names'",
                        Integer.class)).isZero();
                connection.commit(); // Release read metadata locks before the second connection's DDL.
                try (var nextConnection = DriverManager.getConnection(mysql.getJdbcUrl(),
                        mysql.getUsername(), mysql.getPassword());
                        var nextRun = new Liquibase("db/changelog/db.changelog-master.yaml", resources,
                                DatabaseFactory.getInstance().findCorrectDatabaseImplementation(
                                        new JdbcConnection(nextConnection)))) {
                    try (var statement = nextConnection.createStatement()) {
                        statement.execute("SET SESSION lock_wait_timeout = 5");
                    }
                    // Direct API invocations share history caches within this test JVM.
                    ChangeLogHistoryServiceFactory.getInstance()
                            .getChangeLogService(nextRun.getDatabase()).reset();
                    Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
                    nextRun.update(new Contexts(), new LabelExpression());
                }
                assertThat(jdbc.queryForObject("SELECT genre_name FROM title_genres WHERE genre_id = 28",
                        String.class)).isEqualTo("Action");
            }
        }
    }
}
