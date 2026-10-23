package com.team262.moviematch.repository;

import com.team262.moviematch.MySqlTestConfiguration;
import com.team262.moviematch.model.ContentType;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.model.Room;
import com.team262.moviematch.model.RoomStatus;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Set;
import liquibase.integration.spring.SpringLiquibase;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.check_nullability=false",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MySqlTestConfiguration.class)
class RoomPersistenceTests {
    @Autowired
    private RoomRepository rooms;

    @Autowired
    private ParticipantRepository participants;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SpringLiquibase liquibase;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void liquibaseCreatesSchemaOnEmptyMysqlAndCanRunAgain() throws Exception {
        assertThat(jdbc.queryForList("show tables", String.class))
                .contains("rooms", "participants", "room_genres");
        Integer appliedChanges = jdbc.queryForObject("select count(*) from DATABASECHANGELOG", Integer.class);
        assertThat(appliedChanges).isEqualTo(1);
        liquibase.afterPropertiesSet();
        assertThat(jdbc.queryForObject("select count(*) from DATABASECHANGELOG", Integer.class))
                .isEqualTo(appliedChanges);
    }

    @Test
    void roomRoundTripsPreferencesAndNumericGenres() {
        Instant createdAt = Instant.parse("2026-10-24T09:30:00.123456Z");
        Room room = new Room("k7m2qx");
        room.setContentType(ContentType.TV_SERIES);
        room.setMaxDurationMinutes(120);
        room.setCreatedAt(createdAt);
        room.getGenreIds().addAll(Set.of(28, 35));
        rooms.save(room);
        assertThat(room.getId()).isPositive();
        flushAndClear();

        Room loaded = rooms.findById(room.getId()).orElseThrow();
        assertThat(loaded.getCode()).isEqualTo("K7M2QX");
        assertThat(loaded.getStatus()).isEqualTo(RoomStatus.LOBBY);
        assertThat(loaded.getContentType()).isEqualTo(ContentType.TV_SERIES);
        assertThat(loaded.getMaxDurationMinutes()).isEqualTo(120);
        assertThat(loaded.getCreatedAt()).isEqualTo(createdAt);
        assertThat(loaded.getGenreIds()).containsExactlyInAnyOrder(28, 35);
    }

    @Test
    void roomDefaultsAndUnrestrictedPreferencesRoundTrip() {
        Room room = rooms.save(new Room("ABC234"));
        flushAndClear();
        Room loaded = rooms.findById(room.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(RoomStatus.LOBBY);
        assertThat(loaded.getContentType()).isEqualTo(ContentType.MOVIES);
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getMaxDurationMinutes()).isNull();
        assertThat(loaded.getGenreIds()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(RoomStatus.class)
    void everyRoomStatusIsStoredAsText(RoomStatus status) {
        Room room = new Room("ABC234");
        room.setStatus(status);
        rooms.save(room);
        flushAndClear();
        assertThat(rooms.findById(room.getId()).orElseThrow().getStatus()).isEqualTo(status);
        assertThat(entityManager.createNativeQuery("select status from rooms where code = 'ABC234'")
                .getSingleResult()).isEqualTo(status.name());
    }

    @Test
    void findByCodeIsCaseInsensitiveAndUnknownCodeIsEmpty() {
        Room expected = rooms.save(new Room("K7M2QX"));
        rooms.save(new Room("ABC234"));
        flushAndClear();
        assertThat(rooms.findByCode("K7M2QX")).map(Room::getId).contains(expected.getId());
        entityManager.clear();
        assertThat(rooms.findByCode("k7m2qx")).map(Room::getId).contains(expected.getId());
        assertThat(rooms.findByCode("ZZZ999")).isEmpty();
    }

    @Test
    void participantRoundTripsLazyRoomAndAllowsNamesakes() {
        Room room = rooms.save(new Room("ABC234"));
        Participant host = participants.save(new Participant(room, "Alex", true));
        Participant guest = participants.save(new Participant(room, "Alex", false));
        assertThat(host.getId()).isPositive();
        assertThat(guest.getId()).isPositive().isNotEqualTo(host.getId());
        flushAndClear();
        Participant loadedHost = participants.findById(host.getId()).orElseThrow();
        assertThat(loadedHost.getName()).isEqualTo("Alex");
        assertThat(loadedHost.isHost()).isTrue();
        assertThat(Hibernate.isInitialized(loadedHost.getRoom())).isFalse();
        assertThat(loadedHost.getRoom().getId()).isEqualTo(room.getId());
        assertThat(loadedHost.getRoom().getCode()).isEqualTo("ABC234");
        Participant loadedGuest = participants.findById(guest.getId()).orElseThrow();
        assertThat(loadedGuest.getId()).isNotEqualTo(loadedHost.getId());
        assertThat(loadedGuest.getName()).isEqualTo("Alex");
        assertThat(loadedGuest.isHost()).isFalse();
    }

    @Test
    void duplicateRoomCodeIsRejectedOnSaveAndFlush() {
        rooms.saveAndFlush(new Room("K7M2QX"));
        assertMysqlError(1062, () -> rooms.saveAndFlush(new Room("k7m2qx")));
    }

    @Test
    void mysqlRejectsCaseVariantDuplicateEvenWhenJpaNormalizationIsBypassed() {
        rooms.saveAndFlush(new Room("K7M2QX"));
        assertMysqlError(1062, () -> entityManager.createNativeQuery(
                "insert into rooms (code) values ('k7m2qx')").executeUpdate());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABC23", "ABO234", "ABC23!", "AB1234"})
    void invalidInvitationCodesAreRejectedOnSaveAndFlush(String code) {
        assertMysqlError(3819, () -> rooms.saveAndFlush(new Room(code)));
    }

    @ParameterizedTest
    @CsvSource({"status,UNKNOWN", "status,lobby", "content_type,UNKNOWN", "content_type,movies"})
    void databaseRejectsValuesOutsideEnums(String column, String value) {
        rooms.saveAndFlush(new Room("ABC234"));
        assertMysqlError(3819, () -> entityManager.createNativeQuery(
                "update rooms set " + column + " = :value").setParameter("value", value).executeUpdate());
    }

    @ParameterizedTest
    @ValueSource(strings = {"code", "status", "contentType", "createdAt"})
    void mandatoryRoomFieldsAreEnforcedByMysqlOnSaveAndFlush(String field) {
        Room room = new Room("ABC234");
        switch (field) {
            case "code" -> room.setCode(null);
            case "status" -> room.setStatus(null);
            case "contentType" -> room.setContentType(null);
            case "createdAt" -> room.setCreatedAt(null);
            default -> throw new IllegalArgumentException(field);
        }
        assertMysqlError(1048, () -> rooms.saveAndFlush(room));
    }

    @ParameterizedTest
    @ValueSource(strings = {"room", "name"})
    void mandatoryParticipantFieldsAreEnforcedByMysqlOnSaveAndFlush(String field) {
        Room room = rooms.saveAndFlush(new Room("ABC234"));
        Participant participant = new Participant(room, "Alex", false);
        if (field.equals("room")) {
            participant.setRoom(null);
        } else {
            participant.setName(null);
        }
        assertMysqlError(1048, () -> participants.saveAndFlush(participant));
    }

    @ParameterizedTest
    @CsvSource({"rooms,id", "participants,id", "participants,is_host", "room_genres,room_id", "room_genres,genre_id"})
    void remainingNotNullConstraintsAreEnforcedInMysql(String table, String column) {
        Room room = new Room("ABC234");
        room.getGenreIds().add(28);
        rooms.save(room);
        participants.save(new Participant(room, "Alex", false));
        entityManager.flush();
        // Native SQL covers columns that cannot be null in the Java model.
        assertMysqlError(1048, () -> entityManager.createNativeQuery(
                "update " + table + " set " + column + " = null").executeUpdate());
    }

    @Test
    void participantCannotReferenceMissingRoomOnSaveAndFlush() {
        Room missingRoom = entityManager.getReference(Room.class, Long.MAX_VALUE);
        assertMysqlError(1452, () -> participants.saveAndFlush(new Participant(missingRoom, "Alex", false)));
    }

    @Test
    void genreCannotReferenceMissingRoom() {
        entityManager.flush();
        assertMysqlError(1452, () -> entityManager.createNativeQuery(
                "insert into room_genres (room_id, genre_id) values (9223372036854775807, 28)")
                .executeUpdate());
    }

    @Test
    void mysqlRejectsDuplicateGenreInSameRoom() {
        Room room = new Room("ABC234");
        room.getGenreIds().add(28);
        rooms.saveAndFlush(room);
        assertMysqlError(1062, () -> entityManager.createNativeQuery(
                "insert into room_genres (room_id, genre_id) select id, 28 from rooms where code = 'ABC234'")
                .executeUpdate());
    }

    @Test
    void sameGenreCanBelongToDifferentRooms() {
        Room first = new Room("ABC234");
        first.getGenreIds().add(28);
        Room second = new Room("K7M2QX");
        second.getGenreIds().add(28);
        rooms.save(first);
        rooms.save(second);
        flushAndClear();
        assertThat(rooms.findById(first.getId()).orElseThrow().getGenreIds()).containsExactly(28);
        assertThat(rooms.findById(second.getId()).orElseThrow().getGenreIds()).containsExactly(28);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void deletingRoomCleansParticipantsAndGenresWithoutAffectingOtherRooms(boolean nativeDelete) {
        Room room = new Room("ABC234");
        room.getGenreIds().add(28);
        rooms.save(room);
        participants.save(new Participant(room, "Alex", true));
        Room otherRoom = new Room("K7M2QX");
        otherRoom.getGenreIds().add(35);
        rooms.save(otherRoom);
        Participant otherGuest = participants.save(new Participant(otherRoom, "Alex", true));
        flushAndClear();
        if (nativeDelete) {
            entityManager.createNativeQuery("delete from rooms where code = 'ABC234'").executeUpdate();
        } else {
            rooms.deleteById(room.getId());
        }
        flushAndClear();
        assertThat(rooms.findById(room.getId())).isEmpty();
        assertThat(participants.findAll()).extracting(Participant::getId).containsExactly(otherGuest.getId());
        assertThat(jdbc.queryForList("select genre_id from room_genres", Integer.class))
                .containsExactly(35);
        assertThat(rooms.findById(otherRoom.getId())).isPresent();
    }

    @Test
    void databaseDefaultsAlsoApplyOutsideJpa() {
        entityManager.createNativeQuery(
                "insert into rooms (code) values ('ABC234')").executeUpdate();
        entityManager.createNativeQuery("""
                insert into participants (room_id, name)
                select id, 'Alex' from rooms where code = 'ABC234'
                """).executeUpdate();
        flushAndClear();
        Room loaded = rooms.findByCode("ABC234").orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(RoomStatus.LOBBY);
        assertThat(loaded.getContentType()).isEqualTo(ContentType.MOVIES);
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(participants.findAll()).singleElement().satisfies(guest -> assertThat(guest.isHost()).isFalse());
    }

    @Test
    void identityMethodsWorkWithDetachedEntitiesAndUninitializedProxiesWithoutSql() {
        Room room = rooms.save(new Room("ABC234"));
        room.getGenreIds().add(28);
        Participant guest = participants.save(new Participant(room, "Alex", false));
        int originalHash = room.hashCode();
        flushAndClear();
        Room roomProxy = entityManager.getReference(Room.class, room.getId());
        Participant guestProxy = entityManager.getReference(Participant.class, guest.getId());
        var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        assertThat(room.equals(roomProxy)).isTrue();
        assertThat(roomProxy.equals(room)).isTrue();
        assertThat(roomProxy.hashCode()).isEqualTo(originalHash);
        assertThat(guest.equals(guestProxy)).isTrue();
        assertThat(guestProxy.equals(guest)).isTrue();
        assertThat(guestProxy.hashCode()).isEqualTo(guest.hashCode());
        assertThat(roomProxy.toString()).contains(room.getId().toString());
        assertThat(guestProxy.toString()).contains(guest.getId().toString());
        assertThat(Hibernate.isInitialized(roomProxy)).isFalse();
        assertThat(Hibernate.isInitialized(guestProxy)).isFalse();
        assertThat(statistics.getPrepareStatementCount()).isZero();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
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
