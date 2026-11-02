package com.team262.moviematch.service.deck;

import com.team262.moviematch.MySqlTestConfiguration;
import com.team262.moviematch.config.deck.DeckProperties;
import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.ConflictException;
import com.team262.moviematch.exception.ex.DeckBuildException;
import com.team262.moviematch.exception.ex.ExternalServiceException;
import com.team262.moviematch.exception.ex.ForbiddenException;
import com.team262.moviematch.mapper.TitleMapper;
import com.team262.moviematch.model.ContentType;
import com.team262.moviematch.model.DeckBuildStatus;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.model.Room;
import com.team262.moviematch.repository.ParticipantRepository;
import com.team262.moviematch.repository.RoomRepository;
import com.team262.moviematch.repository.RoomTitleRepository;
import com.team262.moviematch.repository.TitleRepository;
import com.team262.moviematch.service.impl.RoomDeckServiceImpl;
import com.team262.moviematch.source.DiscoverFilters;
import com.team262.moviematch.source.DiscoverPage;
import com.team262.moviematch.source.TmdbTitle;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=validate",
        "deck.build-timeout=5s"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MySqlTestConfiguration.class, RoomDeckIntegrationTest.Config.class,
        RoomDeckStore.class, RoomDeckServiceImpl.class, DeckCandidateSelector.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RoomDeckIntegrationTest {
    @Autowired private RoomDeckServiceImpl service;
    @Autowired private RoomDeckStore store;
    @Autowired private RoomRepository rooms;
    @Autowired private ParticipantRepository participants;
    @Autowired private RoomTitleRepository decks;
    @Autowired private TitleRepository titles;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private ControllableSource source;
    @Autowired private DeckCandidateSelector selector;

    @BeforeEach
    void reset() {
        jdbc.update("delete from rooms");
        jdbc.update("delete from titles");
        source.discoverCalls.set(0);
        source.detailCalls.set(0);
        source.pages.clear();
        source.detail = null;
        source.onDiscover = () -> {};
    }

    @Test
    void committedDeckDetailsAndOrderSurviveFreshReadsForEveryParticipant() {
        Fixture fixture = fixture("ABC234");
        List<Long> prepared = service.prepare(fixture.room().getCode(), fixture.host().getId());
        assertThat(prepared).hasSize(20).doesNotHaveDuplicates();
        assertThat(service.getTitleIds("ABC234", fixture.host().getId())).isEqualTo(prepared);
        assertThat(service.getTitleIds("ABC234", fixture.guest().getId())).isEqualTo(prepared);
        int calls = source.detailCalls.get();
        assertThat(service.prepare("ABC234", fixture.host().getId())).isEqualTo(prepared);
        assertThat(source.detailCalls.get()).isEqualTo(calls).isEqualTo(20);
        assertThat(rooms.findById(fixture.room().getId()).orElseThrow().getDeckBuildStatus())
                .isEqualTo(DeckBuildStatus.READY);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var deck = decks.findAllByRoomIdOrderByPositionAsc(fixture.room().getId());
            assertThat(deck).extracting(card -> card.getTitle().getId()).isEqualTo(prepared);
            assertThat(deck).extracting(com.team262.moviematch.model.RoomTitle::getPosition)
                    .containsExactlyElementsOf(IntStream.range(0, 20).boxed().toList());
            var card = deck.getFirst().getTitle();
            assertThat(card.getOverview()).isEqualTo("Overview");
            assertThat(card.getGenres()).containsExactly(new com.team262.moviematch.model.StoredGenre(18, "Drama"));
            assertThat(card.getCast()).containsExactly("Actor A", "Actor B");
            assertThat(card.getCreators()).containsExactly("Creator");
            assertThat(card.getDirector()).isEqualTo("Director");
            assertThat(card.getRuntimeMinutes()).isEqualTo(90);
            assertThat(card.getVoteAverage()).isEqualByComparingTo("8.5");
            assertThat(card.getBudget()).isEqualTo(1000000L);
            assertThat(card.getRevenue()).isEqualTo(5000000L);
            assertThat(card.getOriginalLanguage()).isEqualTo("en");
        });
    }

    @Test
    void twoConcurrentRequestsForDifferentRoomsReuseTheSameTitles() throws Exception {
        Fixture first = fixture("ABC234");
        Fixture second = fixture("DEF567");
        // Simulate separate incoming HTTP requests; production has no background executor.
        var callers = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var one = callers.submit(() -> service.prepare("ABC234", first.host().getId()));
            var two = callers.submit(() -> service.prepare("DEF567", second.host().getId()));
            assertThat(one.get(10, TimeUnit.SECONDS)).containsExactlyInAnyOrderElementsOf(
                    two.get(10, TimeUnit.SECONDS));
            assertThat(titles.count()).isEqualTo(20);
            assertThat(decks.count()).isEqualTo(40);
        } finally {
            callers.shutdownNow();
        }
    }

    @Test
    void repeatedPreparationDuringAnActiveBuildIsRejectedBeforeExtraProviderCalls() {
        Fixture fixture = fixture("ABC234");
        source.onDiscover = () -> assertThatThrownBy(() -> service.prepare("ABC234", fixture.host().getId()))
                .isInstanceOf(ConflictException.class).extracting("code")
                .isEqualTo(ErrorCode.DECK_BUILD_IN_PROGRESS);
        assertThat(service.prepare("ABC234", fixture.host().getId())).hasSize(20);
        assertThat(source.discoverCalls.get()).isEqualTo(1);
        assertThat(source.detailCalls.get()).isEqualTo(20);
    }

    @Test
    void failuresLeaveNoPartialDeckAndPermitRetry() {
        Fixture fixture = fixture("ABC234");
        source.detail = id -> { throw new ExternalServiceException(); };
        assertThatThrownBy(() -> service.prepare("ABC234", fixture.host().getId())).isInstanceOf(ExternalServiceException.class);
        assertThat(rooms.findById(fixture.room().getId()).orElseThrow().getDeckBuildStatus())
                .isEqualTo(DeckBuildStatus.FAILED);
        assertThat(decks.count()).isZero();
        assertThat(titles.count()).isZero();
        source.detail = null;
        assertThat(service.prepare("ABC234", fixture.host().getId())).hasSize(20);
    }

    @Test
    void databaseFailureRollsBackAllTitlesAndDeckEntries() {
        Fixture fixture = fixture("ABC234");
        source.detail = id -> copyWith(DeckCandidateSelectorTest.card(id, ContentType.MOVIES, 90),
                id == 119 ? "x".repeat(600) : "Valid card", "Director");
        assertThatThrownBy(() -> service.prepare("ABC234", fixture.host().getId())).isInstanceOf(
                        org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(rooms.findById(fixture.room().getId()).orElseThrow().getDeckBuildStatus())
                .isEqualTo(DeckBuildStatus.FAILED);
        assertThat(decks.count()).isZero();
        assertThat(titles.count()).isZero();
    }

    @Test
    void fullTvDirectorListIsNotTruncatedAtOldVarcharLimit() {
        Fixture fixture = fixture("ABC234");
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                rooms.findById(fixture.room().getId()).orElseThrow().setContentType(ContentType.TV_SERIES));
        source.detail = id -> copyWith(DeckCandidateSelectorTest.card(id, ContentType.TV_SERIES, 45),
                "Series", "Director ".repeat(200));
        List<Long> ids = service.prepare("ABC234", fixture.host().getId());
        assertThat(titles.findById(ids.getFirst()).orElseThrow().getDirector())
                .isEqualTo("Director ".repeat(200));
    }

    @Test
    void permissionsAndNotReadyAreCheckedBeforeCallingProvider() {
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
    void expiredLeaseCanBeReclaimedAndOldAttemptCannotPersistOrClearNewLease() {
        Fixture fixture = fixture("ABC234");
        RoomDeckStore.Claim old = store.claim("ABC234", fixture.host().getId(), Duration.ofSeconds(5));
        jdbc.update("update rooms set deck_build_expires_at = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND where id = ?",
                fixture.room().getId());
        RoomDeckStore.Claim current = store.claim("ABC234", fixture.host().getId(), Duration.ofSeconds(5));
        List<TmdbTitle> cards = IntStream.range(0, 20).mapToObj(i ->
                DeckCandidateSelectorTest.card(100L + i, ContentType.MOVIES, 90)).toList();
        assertThatThrownBy(() -> store.complete(old, cards, new DeckDeadline(Duration.ofSeconds(5))))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo(ErrorCode.DECK_BUILD_STALE);
        store.fail(old);
        Room loaded = rooms.findById(fixture.room().getId()).orElseThrow();
        assertThat(loaded.getDeckBuildToken()).isEqualTo(current.token());
        assertThat(loaded.getDeckBuildStatus()).isEqualTo(DeckBuildStatus.BUILDING);
        assertThat(decks.count()).isZero();
        store.fail(current);
    }

    @Test
    void aSecondServiceInstanceCannotClaimAnActiveLease() {
        Fixture fixture = fixture("ABC234");
        RoomDeckStore.Claim claim = store.claim("ABC234", fixture.host().getId(), Duration.ofSeconds(5));
        try {
            assertThatThrownBy(() -> store.claim("ABC234", fixture.host().getId(), Duration.ofSeconds(5)))
                    .isInstanceOf(ConflictException.class).extracting("code")
                    .isEqualTo(ErrorCode.DECK_BUILD_IN_PROGRESS);
        } finally {
            store.fail(claim);
        }
    }

    @Test
    void changingFiltersDuringBuildPreventsSavingStaleDeck() {
        Fixture fixture = fixture("ABC234");
        RoomDeckStore.Claim claim = store.claim("ABC234", fixture.host().getId(), Duration.ofSeconds(5));
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                rooms.findById(fixture.room().getId()).orElseThrow().setMaxDurationMinutes(60));
        List<TmdbTitle> cards = IntStream.range(0, 20).mapToObj(i ->
                DeckCandidateSelectorTest.card(100L + i, ContentType.MOVIES, 90)).toList();
        try {
            assertThatThrownBy(() -> store.complete(claim, cards, new DeckDeadline(Duration.ofSeconds(5))))
                    .isInstanceOf(ConflictException.class).extracting("code").isEqualTo(ErrorCode.DECK_BUILD_STALE);
            assertThat(decks.count()).isZero();
            assertThat(titles.count()).isZero();
        } finally {
            store.fail(claim);
        }
    }

    @Test
    void expiredDeadlineAfterProviderReturnsDoesNotSaveALateDeck() {
        Fixture fixture = fixture("ABC234");
        DeckProperties shortLimits = new DeckProperties(50, 4, 8, 30, Duration.ofMillis(10));
        var shortService = new RoomDeckServiceImpl(store, selector, shortLimits);
        source.onDiscover = () -> {
            try {
                TimeUnit.MILLISECONDS.sleep(30);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(ex);
            }
        };
        assertThatThrownBy(() -> shortService.prepare("ABC234", fixture.host().getId()))
                .isInstanceOf(DeckBuildException.class).extracting("code")
                .isEqualTo(ErrorCode.DECK_BUILD_TIMEOUT);
        assertThat(rooms.findById(fixture.room().getId()).orElseThrow().getDeckBuildStatus())
                .isEqualTo(DeckBuildStatus.FAILED);
        assertThat(decks.count()).isZero();
        assertThat(source.detailCalls.get()).isZero();
    }
    @Test
    void aNewRoomCanRefreshGenreNamesWithoutDuplicatingTitlesOrGenres() {
        Fixture first = fixture("ABC234");
        List<Long> ids = service.prepare("ABC234", first.host().getId());
        Fixture second = fixture("DEF567");
        source.detail = id -> {
            TmdbTitle card = DeckCandidateSelectorTest.card(id, ContentType.MOVIES, 90);
            return new TmdbTitle(card.tmdbId(), card.mediaType(), card.title(), card.releaseYear(),
                    card.overview(), card.posterPath(), card.popularity(), card.voteAverage(),
                    card.runtimeMinutes(), List.of(new com.team262.moviematch.source.TitleGenre(18, "Драма")),
                    card.director(), card.creators(), card.cast(), card.ageRating(), card.budget(),
                    card.revenue(), card.originalLanguage());
        };
        assertThat(service.prepare("DEF567", second.host().getId()))
                .containsExactlyInAnyOrderElementsOf(ids);
        assertThat(titles.count()).isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM title_genres", Integer.class)).isEqualTo(20);
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                assertThat(titles.findById(ids.getFirst()).orElseThrow().getGenres())
                        .containsExactly(new com.team262.moviematch.model.StoredGenre(18, "Драма")));
    }

    private Fixture fixture(String code) {
        return new TransactionTemplate(transactions).execute(status -> {
            Room room = rooms.saveAndFlush(new Room(code));
            Participant host = participants.save(new Participant(room, "Host", true,
                    UUID.randomUUID().toString(), LocalDateTime.now()));
            Participant guest = participants.save(new Participant(room, "Guest", false,
                    UUID.randomUUID().toString(), LocalDateTime.now()));
            return new Fixture(room, host, guest);
        });
    }

    private TmdbTitle copyWith(TmdbTitle card, String name, String director) {
        return new TmdbTitle(card.tmdbId(), card.mediaType(), name, card.releaseYear(),
                card.overview(), card.posterPath(), card.popularity(), card.voteAverage(),
                card.runtimeMinutes(), card.genres(), director, card.creators(), card.cast(),
                card.ageRating(), card.budget(), card.revenue(), card.originalLanguage());
    }

    private record Fixture(Room room, Participant host, Participant guest) {
    }

    static class ControllableSource extends DeckCandidateSelectorTest.FakeSource {
        volatile Runnable onDiscover = () -> {};

        ControllableSource() {
            super(1);
        }

        @Override
        public DiscoverPage discover(ContentType type, DiscoverFilters filters, int page) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            onDiscover.run();
            return super.discover(type, filters, page);
        }

        @Override
        public TmdbTitle getDetails(ContentType type, long id) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return super.getDetails(type, id);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DeckProperties.class)
    static class Config {
        @Bean ControllableSource source() { return new ControllableSource(); }
        @Bean TitleMapper titleMapper() { return Mappers.getMapper(TitleMapper.class); }

    }
}
