package com.team262.moviematch.service.history;

import static org.assertj.core.api.Assertions.*;
import com.team262.moviematch.model.*;
import com.team262.moviematch.model.history.ArchivedParticipant;
import com.team262.moviematch.model.history.GameSessionArchive;
import com.team262.moviematch.repository.*;
import com.team262.moviematch.service.game.GameResultService;
import com.team262.moviematch.service.account.ParticipantLinkService;
import com.team262.moviematch.dto.history.HistoryDtos;
import com.team262.moviematch.dto.history.ProfileResponse;
import com.team262.moviematch.exception.ex.NotFoundException;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {"title.source=stub",
        "spring.datasource.url=jdbc:h2:mem:history;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.liquibase.change-log=classpath:db/changelog/account-h2.yaml"})
class HistoryIntegrationTest {
    @Autowired UserRepository users;
    @Autowired RoomRepository rooms;
    @Autowired ParticipantRepository participants;
    @Autowired TitleRepository titles;
    @Autowired RoomTitleRepository cards;
    @Autowired VoteRepository votes;
    @Autowired GameSessionArchiveRepository archives;
    @Autowired GameResultService results;
    @Autowired HistoryService history;
    @Autowired ProfileService profiles;
    @Autowired ParticipantLinkService participantLinks;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void historyAndPersonalStatsSurviveRoomDeletionAndRemainPrivate() {
        TransactionTemplate transaction = new TransactionTemplate(transactions);
        User user = users.save(new User("history@example.com", "hash", "Anna"));
        User stranger = users.save(new User("stranger@example.com", "hash", "Other"));
        ProfileResponse empty = profiles.profile(user);
        assertThat(empty.sessionsCount()).isZero();
        assertThat(empty.genreMix()).isEmpty();
        assertThat(empty.favoriteGenre()).isNull();
        assertThat(empty.leastLikedGenre()).isNull();
        assertThat(empty.decadeMix()).isEmpty();
        assertThat(empty.favoriteDecade()).isNull();
        assertThat(empty.favoriteActors()).isEmpty();
        TestRound round = transaction.execute(status -> {
            Room room = rooms.save(new Room("HIS123"));
            room.setStatus(RoomStatus.VOTING);
            Participant member = new Participant(room, "Anna", true, UUID.randomUUID().toString(),
                    LocalDateTime.now());
            participants.save(member);
            Participant guest = participants.save(new Participant(room, "Guest", false,
                    UUID.randomUUID().toString(), LocalDateTime.now()));
            for (int position = 0; position < 20; position++) {
                Title title = new Title(1000L + position, ContentType.MOVIES, "Film " + position);
                title.getGenres().add(new StoredGenre(28, "Action"));
                // PROFILE-STAT-2/3 fixtures: liked cards are positions 0, 1, 2
                title.setReleaseYear(position < 2 ? 1997 : 2024);
                title.getCast().add("Zendaya");                       // on every title
                if (position < 2) {
                    title.getCast().add("Ryan Gosling");              // on two liked titles
                }
                title.getCast().add("Extra " + position);             // one title each: below the actor threshold
                titles.save(title);
                RoomTitle card = cards.save(new RoomTitle(room, title, position));
                votes.save(new Vote(member, card, position < 3));
                votes.save(new Vote(guest, card, position == 0));
            }
            results.finishIfReady(room);
            results.finishIfReady(room);
            return new TestRound(room.getId(), member.getId(), member.getToken());
        });
        assertThat(archives.count()).isEqualTo(1);
        assertThat(history.history(user.getId(), 0, 10).items()).isEmpty();
        participantLinks.link(user, round.participantToken());
        transaction.executeWithoutResult(status -> {
            GameSessionArchive linkedArchive = archives.findBySourceRoomId(round.roomId()).orElseThrow();
            assertThat(linkedArchive.getParticipants())
                    .filteredOn(member -> user.getId().equals(member.getUserId()))
                    .extracting(ArchivedParticipant::getSourceParticipantId)
                    .containsExactly(round.participantId());
            assertThat(linkedArchive.getVotes()).hasSize(40);
            assertThat(linkedArchive.getVotes())
                    .filteredOn(vote -> round.participantId().equals(vote.getSourceParticipantId()))
                    .hasSize(20);
        });
        Long sessionId = history.history(user.getId(), 0, 10).items().getFirst().sessionId();
        HistoryDtos.Details before = history.details(user.getId(), sessionId);
        assertThat(before.cards()).hasSize(20);
        assertThat(before.session().likedCount()).isEqualTo(3);
        assertThat(before.session().winnerTitles()).hasSize(1);
        assertThat(before.cards().getFirst().likesCount()).isEqualTo(2);
        assertThat(before.cards().getFirst().myVote()).isTrue();
        transaction.executeWithoutResult(status -> rooms.deleteById(round.roomId()));
        assertThat(history.details(user.getId(), sessionId)).isEqualTo(before);
        assertThat(votes.count()).isZero();
        ProfileResponse profile = profiles.profile(user);
        assertThat(profile.sessionsCount()).isEqualTo(1);
        assertThat(profile.ratedCount()).isEqualTo(20);
        assertThat(profile.likedCount()).isEqualTo(3);
        assertThat(profile.favoriteGenre().id()).isEqualTo(28);
        assertThat(profile.favoriteGenre().percentage()).isEqualTo(100.0);

        // PROFILE-STAT-1: Action was rated 20 times, liked 3 -> 17 dislikes = 85%
        assertThat(profile.leastLikedGenre()).isNotNull();
        assertThat(profile.leastLikedGenre().id()).isEqualTo(28);
        assertThat(profile.leastLikedGenre().dislikesCount()).isEqualTo(17);
        assertThat(profile.leastLikedGenre().ratedCount()).isEqualTo(20);
        assertThat(profile.leastLikedGenre().dislikePercentage()).isEqualTo(85.0);

        // PROFILE-STAT-2: liked years 1997, 1997, 2024 -> 1990s: 2 (66.7%), 2020s: 1 (33.3%)
        assertThat(profile.decadeMix()).extracting(ProfileResponse.DecadeStat::decade)
                .containsExactly(1990, 2020);
        assertThat(profile.decadeMix()).extracting(ProfileResponse.DecadeStat::percentage)
                .containsExactly(66.7, 33.3);
        assertThat(profile.favoriteDecade().label()).isEqualTo("1990s");
        assertThat(profile.favoriteDecade().likedCount()).isEqualTo(2);

        // PROFILE-STAT-3: Zendaya on 3 liked titles, Ryan Gosling on 2; "Extra N" have 1 each (filtered out)
        assertThat(profile.favoriteActors()).extracting(ProfileResponse.ActorStat::name)
                .containsExactly("Zendaya", "Ryan Gosling");
        assertThat(profile.favoriteActors()).extracting(ProfileResponse.ActorStat::likedTitlesCount)
                .containsExactly(3L, 2L);

        assertThat(history.history(stranger.getId(), 0, 10).items()).isEmpty();
        assertThat(profiles.profile(stranger).favoriteActors()).isEmpty();
        assertThatThrownBy(() -> history.details(stranger.getId(), sessionId))
                .isInstanceOf(NotFoundException.class);
    }

    private record TestRound(Long roomId, Long participantId, String participantToken) {
    }
}