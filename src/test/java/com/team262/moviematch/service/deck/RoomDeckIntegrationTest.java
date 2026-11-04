package com.team262.moviematch.service.deck;

import com.team262.moviematch.MySqlTestConfiguration;
import com.team262.moviematch.config.deck.DeckProperties;
import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.ConflictException;
import com.team262.moviematch.exception.ex.ExternalServiceException;
import com.team262.moviematch.exception.ex.ForbiddenException;
import com.team262.moviematch.exception.ex.NotFoundException;
import com.team262.moviematch.mapper.TitleMapper;
import com.team262.moviematch.model.ContentType;
import com.team262.moviematch.model.DeckBuildStatus;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.model.Room;
import com.team262.moviematch.model.RoomStatus;
import com.team262.moviematch.model.RoomTitle;
import com.team262.moviematch.repository.ParticipantRepository;
import com.team262.moviematch.repository.RoomRepository;
import com.team262.moviematch.repository.RoomTitleRepository;
import com.team262.moviematch.repository.TitleRepository;
import com.team262.moviematch.service.impl.RoomDeckServiceImpl;
import com.team262.moviematch.source.TmdbTitle;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false, properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MySqlTestConfiguration.class, RoomDeckIntegrationTest.Config.class,
        RoomDeckStore.class, RoomDeckServiceImpl.class, DeckCandidateSelector.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RoomDeckIntegrationTest {
    @Autowired private RoomDeckServiceImpl service;
    @Autowired private RoomRepository rooms;
    @Autowired private ParticipantRepository participants;
    @Autowired private RoomTitleRepository decks;
    @Autowired private TitleRepository titles;
    @Autowired private DeckCandidateSelectorTest.FakeSource source;

    @BeforeEach
    void reset() {
        rooms.deleteAll();
        titles.deleteAll();
        source.discoverCalls.set(0);
        source.detailCalls.set(0);
        source.pages.clear();
        source.detail = null;
    }

    @Test
    void committedDeckAndOrderSurviveFreshReadsForEveryParticipant() {
        Fixture fixture = fixture("ABC234");
        List<Long> prepared = service.prepare(fixture.room().getId(), fixture.host().getId());
        assertThat(prepared).hasSize(20).doesNotHaveDuplicates();
        assertThat(service.getTitleIds("ABC234", fixture.host().getId())).isEqualTo(prepared);
        assertThat(service.getTitleIds("ABC234", fixture.guest().getId())).isEqualTo(prepared);
        assertThat(service.prepare("ABC234", fixture.host().getId())).isEqualTo(prepared);
        assertThat(source.detailCalls.get()).isEqualTo(20);
        assertThat(rooms.findById(fixture.room().getId()).orElseThrow().getDeckBuildStatus())
                .isEqualTo(DeckBuildStatus.READY);
        List<RoomTitle> deck = decks.findAllByRoomIdOrderByPositionAsc(fixture.room().getId());
        assertThat(deck).extracting(card -> card.getTitle().getId()).isEqualTo(prepared);
        assertThat(deck).extracting(RoomTitle::getPosition)
                .containsExactlyElementsOf(IntStream.range(0, 20).boxed().toList());
        assertThat(titles.findById(prepared.getFirst()).orElseThrow().getOverview()).isEqualTo("Overview");
    }

    @Test
    void differentRoomsReuseSharedTitles() {
        Fixture first = fixture("ABC234");
        Fixture second = fixture("DEF567");
        List<Long> one = service.prepare("ABC234", first.host().getId());
        List<Long> two = service.prepare("DEF567", second.host().getId());
        assertThat(two).containsExactlyInAnyOrderElementsOf(one);
        assertThat(titles.count()).isEqualTo(20);
        assertThat(decks.count()).isEqualTo(40);
    }

    @Test
    void sourceFailureLeavesNoPartialDeckAndPermitsRetry() {
        Fixture fixture = fixture("ABC234");
        source.detail = id -> { throw new ExternalServiceException(); };
        assertThatThrownBy(() -> service.prepare("ABC234", fixture.host().getId()))
                .isInstanceOf(ExternalServiceException.class);
        assertThat(decks.count()).isZero();
        assertThat(titles.count()).isZero();
        assertThat(rooms.findById(fixture.room().getId()).orElseThrow().getDeckBuildStatus())
                .isEqualTo(DeckBuildStatus.IDLE);
        source.detail = null;
        assertThat(service.prepare("ABC234", fixture.host().getId())).hasSize(20);
    }

    @Test
    void databaseFailureRollsBackTitlesRoomEntriesAndStatus() {
        Fixture fixture = fixture("ABC234");
        source.detail = id -> copyWith(DeckCandidateSelectorTest.card(id, ContentType.MOVIES, 90),
                id == 119 ? "x".repeat(600) : "Valid card", "Director");
        assertThatThrownBy(() -> service.prepare("ABC234", fixture.host().getId()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(decks.count()).isZero();
        assertThat(titles.count()).isZero();
        assertThat(rooms.findById(fixture.room().getId()).orElseThrow().getDeckBuildStatus())
                .isEqualTo(DeckBuildStatus.IDLE);
    }

    @Test
    void permissionsAreCheckedBeforeCallingSource() {
        Fixture one = fixture("ABC234");
        Fixture other = fixture("DEF567");
        assertThatThrownBy(() -> service.prepare("ABC234", one.guest().getId()))
                .isInstanceOf(ForbiddenException.class).extracting("code").isEqualTo(ErrorCode.NOT_HOST);
        assertThatThrownBy(() -> service.prepare("ABC234", other.host().getId()))
                .isInstanceOf(ForbiddenException.class).extracting("code").isEqualTo(ErrorCode.WRONG_ROOM);
        assertThatThrownBy(() -> service.getTitleIds("ABC234", one.guest().getId()))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo(ErrorCode.RESULT_NOT_READY);
        assertThat(source.discoverCalls.get()).isZero();
    }

    @Test
    void missingRoomIsNotFoundForBothIdAndCode() {
        assertThatThrownBy(() -> service.prepare(Long.MAX_VALUE, 1))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.prepare("ZZZ999", 1))
                .isInstanceOf(NotFoundException.class);
        assertThat(source.discoverCalls.get()).isZero();
    }

    @Test
    void emptyDeckCannotBePreparedOutsideLobby() {
        Fixture fixture = fixture("ABC234");
        Room room = fixture.room();
        room.setStatus(RoomStatus.VOTING);
        rooms.save(room);
        assertThatThrownBy(() -> service.prepare("ABC234", fixture.host().getId()))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo(ErrorCode.ROOM_NOT_JOINABLE);
        assertThat(source.discoverCalls.get()).isZero();
    }

    @Test
    void tvDetailsAreStoredWithoutTruncatingDirectorList() {
        Fixture fixture = fixture("ABC234");
        Room room = fixture.room();
        room.setContentType(ContentType.TV_SERIES);
        rooms.save(room);
        source.detail = id -> copyWith(DeckCandidateSelectorTest.card(id, ContentType.TV_SERIES, 45),
                "Series", "Director ".repeat(200));
        List<Long> ids = service.prepare("ABC234", fixture.host().getId());
        assertThat(titles.findById(ids.getFirst()).orElseThrow().getDirector())
                .isEqualTo("Director ".repeat(200));
    }

    private Fixture fixture(String code) {
        Room room = rooms.save(new Room(code));
        Participant host = participants.save(new Participant(room, "Host", true,
                UUID.randomUUID().toString(), LocalDateTime.now()));
        Participant guest = participants.save(new Participant(room, "Guest", false,
                UUID.randomUUID().toString(), LocalDateTime.now()));
        return new Fixture(room, host, guest);
    }

    private TmdbTitle copyWith(TmdbTitle card, String name, String director) {
        return new TmdbTitle(card.tmdbId(), card.mediaType(), name, card.releaseYear(),
                card.overview(), card.posterPath(), card.popularity(), card.voteAverage(),
                card.runtimeMinutes(), card.genres(), director, card.creators(), card.cast(),
                card.ageRating(), card.budget(), card.revenue(), card.originalLanguage());
    }

    private record Fixture(Room room, Participant host, Participant guest) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DeckProperties.class)
    static class Config {
        @Bean
        DeckCandidateSelectorTest.FakeSource source() {
            return new DeckCandidateSelectorTest.FakeSource(1);
        }

        @Bean
        TitleMapper titleMapper() {
            return Mappers.getMapper(TitleMapper.class);
        }
    }
}
