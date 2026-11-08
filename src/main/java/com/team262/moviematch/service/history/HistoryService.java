package com.team262.moviematch.service.history;
import com.team262.moviematch.dto.history.HistoryDtos.*;
import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.NotFoundException;
import com.team262.moviematch.model.Title;
import com.team262.moviematch.model.history.GameSessionArchive;
import com.team262.moviematch.model.history.ArchivedVote;
import com.team262.moviematch.repository.GameSessionArchiveRepository;
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
        return new Details(summary(session, userId),
                session.getParticipants().stream()
                        .sorted(Comparator.comparing(member -> member.getSourceParticipantId()))
                        .map(member -> new ParticipantDto(member.getDisplayName(), member.isHost(),
                                Objects.equals(member.getUserId(), userId))).toList(),
                session.getCards().stream().map(card -> new CardDto(card.getPosition(),
                        title(card.getTitle()), card.getLikesCount(), card.isWinner(),
                        votes.get(card.getSourceCardId()))).toList());
    }
    private Summary summary(GameSessionArchive session, Long userId) {
        Set<Long> mine = participantIds(session, userId);
        List<ArchivedVote> votes = session.getVotes().stream()
                .filter(vote -> mine.contains(vote.getSourceParticipantId())).toList();
        return new Summary(session.getId(), session.getFinishedAt(), session.getContentType(),
                session.getParticipantCount(), session.getCards().stream().filter(card -> card.isWinner())
                        .map(card -> title(card.getTitle())).toList(),
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
                title.getRuntimeMinutes(), title.getVoteAverage());
    }
}

