package com.team262.moviematch.service.history;
import com.team262.moviematch.dto.history.HistoryDtos.*;
import com.team262.moviematch.dto.voc.GenreDto;
import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.NotFoundException;
import com.team262.moviematch.model.Title;
import com.team262.moviematch.model.User;
import com.team262.moviematch.model.UserAvatar;
import com.team262.moviematch.model.history.ArchivedCard;
import com.team262.moviematch.model.history.GameSessionArchive;
import com.team262.moviematch.model.history.ArchivedVote;
import com.team262.moviematch.repository.GameSessionArchiveRepository;
import com.team262.moviematch.repository.UserAvatarRepository;
import com.team262.moviematch.repository.UserRepository;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HistoryService {
    private final GameSessionArchiveRepository archives;
    private final UserRepository users;
    private final UserAvatarRepository avatars;
    public HistoryPage history(Long userId, int page, int size) {
        Page<GameSessionArchive> result = archives.findDistinctByParticipants_UserId(
                userId, PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("finishedAt"), Sort.Order.desc("id"))));
        return new HistoryPage(result.getContent().stream().map(session -> summary(session, userId)).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }
    public Details details(Long userId, Long sessionId) {
        GameSessionArchive session = archives.findDistinctByIdAndParticipants_UserId(
                sessionId, userId).orElseThrow(() ->
                new NotFoundException(ErrorCode.HISTORY_NOT_FOUND, "Session not found"));
        Set<Long> mine = participantIds(session, userId);
        Map<Long, Boolean> votes = session.getVotes().stream()
                .filter(vote -> mine.contains(vote.getSourceParticipantId()))
                .collect(Collectors.toMap(vote -> vote.getSourceCardId(), vote -> vote.isLiked()));
        Set<Long> linkedUserIds = session.getParticipants().stream()
                .map(member -> member.getUserId()).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, User> linkedUsers = users.findAllById(linkedUserIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        return new Details(summary(session, userId),
                session.getParticipants().stream()
                        .sorted(Comparator.comparing(member -> member.getSourceParticipantId()))
                        .map(member -> new ParticipantDto(member.getDisplayName(), member.isHost(),
                                Objects.equals(member.getUserId(), userId),
                                avatarUrl(sessionId, member.getSourceParticipantId(),
                                        linkedUsers.get(member.getUserId())))).toList(),
                session.getCards().stream().map(card -> new CardDto(card.getPosition(),
                        title(card.getTitle()), card.getLikesCount(), card.isWinner(),
                        votes.get(card.getSourceCardId()))).toList());
    }
    public UserAvatar participantAvatar(Long viewerId, Long sessionId, Long participantId) {
        GameSessionArchive session = archives.findDistinctByIdAndParticipants_UserId(
                sessionId, viewerId).orElseThrow(HistoryService::historyNotFound);
        Long ownerId = session.getParticipants().stream()
                .filter(member -> Objects.equals(member.getSourceParticipantId(), participantId))
                .map(member -> member.getUserId()).filter(Objects::nonNull)
                .findFirst().orElseThrow(HistoryService::historyNotFound);
        User owner = users.findById(ownerId).orElseThrow(HistoryService::historyNotFound);
        if (owner.getAvatarVersion() == null) {
            throw historyNotFound();
        }
        return avatars.findById(ownerId).orElseThrow(HistoryService::historyNotFound);
    }

    private String avatarUrl(Long sessionId, Long participantId, User user) {
        return user == null || user.getAvatarVersion() == null ? null
                : "/me/history/" + sessionId + "/participants/" + participantId
                        + "/avatar?v=" + user.getAvatarVersion();
    }

    private static NotFoundException historyNotFound() {
        return new NotFoundException(ErrorCode.HISTORY_NOT_FOUND, "Session or avatar not found");
    }
    private Summary summary(GameSessionArchive session, Long userId) {
        Set<Long> mine = participantIds(session, userId);
        List<ArchivedVote> votes = session.getVotes().stream()
                .filter(vote -> mine.contains(vote.getSourceParticipantId())).toList();
        List<ArchivedCard> orderedCards = session.getCards().stream()
                .sorted(Comparator.comparingInt(ArchivedCard::getPosition)).toList();
        return new Summary(session.getId(), session.getFinishedAt(), session.getContentType(),
                session.getParticipantCount(), orderedCards.size(),
                orderedCards.stream().filter(ArchivedCard::isWinner).map(this::winner).toList(),
                orderedCards.stream().limit(3).map(card -> title(card.getTitle())).toList(),
                votes.size(), votes.stream().filter(vote -> vote.isLiked()).count());
    }
    private Set<Long> participantIds(GameSessionArchive session, Long userId) {
        return session.getParticipants().stream().filter(member -> Objects.equals(member.getUserId(), userId))
                .map(member -> member.getSourceParticipantId()).collect(Collectors.toSet());
    }
    private TitleDto title(Title title) {
        String path = title.getPosterPath();
        String url = path == null || path.isBlank() ? null :
                (path.startsWith("/demo/") || path.startsWith("https://") || path.startsWith("http://")
                        ? path : "https://image.tmdb.org/t/p/w500" + path);
        return new TitleDto(title.getId(), title.getTitle(), title.getReleaseYear(), url,
                title.getRuntimeMinutes(), title.getVoteAverage(), genres(title));
    }
    private WinnerTitleDto winner(ArchivedCard card) {
        TitleDto title = title(card.getTitle());
        return new WinnerTitleDto(title.id(), title.title(), title.year(), title.posterUrl(),
                title.runtimeMinutes(), title.voteAverage(), title.genres(), card.getLikesCount());
    }
    private List<GenreDto> genres(Title title) {
        return title.getGenres().stream()
                .sorted(Comparator.comparingInt(genre -> genre.getId()))
                .map(genre -> new GenreDto(genre.getId(), genre.getName())).toList();
    }
}

