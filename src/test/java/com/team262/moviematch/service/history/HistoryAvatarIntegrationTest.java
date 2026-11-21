package com.team262.moviematch.service.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team262.moviematch.dto.history.HistoryDtos;
import com.team262.moviematch.exception.ex.NotFoundException;
import com.team262.moviematch.model.ContentType;
import com.team262.moviematch.model.User;
import com.team262.moviematch.model.history.ArchivedParticipant;
import com.team262.moviematch.model.history.GameSessionArchive;
import com.team262.moviematch.repository.GameSessionArchiveRepository;
import com.team262.moviematch.repository.UserRepository;
import com.team262.moviematch.service.account.AccountAvatarService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

@SpringBootTest(properties = {"title.source=stub",
        "spring.datasource.url=jdbc:h2:mem:history-avatar;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.liquibase.change-log=classpath:db/changelog/account-h2.yaml"})
class HistoryAvatarIntegrationTest {
    @Autowired UserRepository users;
    @Autowired GameSessionArchiveRepository archives;
    @Autowired HistoryService history;
    @Autowired AccountAvatarService avatars;

    @Test
    void showsCurrentAvatarToSessionMembersButNotToOutsiders() {
        User viewer = users.save(new User("avatar-viewer@example.com", "hash", "Viewer"));
        User friend = users.save(new User("avatar-friend@example.com", "hash", "Friend"));
        User outsider = users.save(new User("avatar-outsider@example.com", "hash", "Outsider"));
        ArchivedParticipant host = new ArchivedParticipant(101L, "Viewer", true);
        host.setUserId(viewer.getId());
        ArchivedParticipant member = new ArchivedParticipant(102L, "Friend", false);
        member.setUserId(friend.getId());
        ArchivedParticipant guest = new ArchivedParticipant(103L, "Guest", false);
        GameSessionArchive archive = archives.saveAndFlush(new GameSessionArchive(
                999999L, "AVT123", ContentType.MOVIES, Instant.now(), Instant.now(),
                List.of(host, member, guest), List.of()));

        assertThat(history.details(viewer.getId(), archive.getId()).participants())
                .extracting(HistoryDtos.ParticipantDto::avatarUrl)
                .containsExactly(null, null, null);

        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        avatars.upload(friend, new MockMultipartFile("file", "friend.png", "image/png", png));
        List<HistoryDtos.ParticipantDto> members = history.details(viewer.getId(), archive.getId()).participants();
        assertThat(members.get(0).avatarUrl()).isNull();
        assertThat(members.get(1).avatarUrl()).startsWith(
                "/me/history/" + archive.getId() + "/participants/102/avatar?v=");
        assertThat(members.get(2).avatarUrl()).isNull();
        assertThat(history.participantAvatar(viewer.getId(), archive.getId(), 102L)
                .getImageBytes()).isEqualTo(png);
        assertThatThrownBy(() -> history.participantAvatar(outsider.getId(), archive.getId(), 102L))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> history.participantAvatar(viewer.getId(), archive.getId(), 103L))
                .isInstanceOf(NotFoundException.class);

        avatars.delete(friend);
        assertThat(history.details(viewer.getId(), archive.getId()).participants().get(1).avatarUrl()).isNull();
    }
}
