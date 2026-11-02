package com.team262.moviematch.service.deck;

import com.team262.moviematch.config.deck.DeckProperties;
import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.ConflictException;
import com.team262.moviematch.mapper.TitleMapper;
import com.team262.moviematch.model.DeckBuildStatus;
import com.team262.moviematch.model.Room;
import com.team262.moviematch.model.RoomTitle;
import com.team262.moviematch.model.Title;
import com.team262.moviematch.repository.RoomRepository;
import com.team262.moviematch.repository.RoomTitleRepository;
import com.team262.moviematch.repository.TitleRepository;
import com.team262.moviematch.source.TmdbTitle;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomDeckStore {
    private final RoomRepository roomRepository;
    private final RoomTitleRepository roomTitleRepository;
    private final TitleRepository titleRepository;
    private final TitleMapper titleMapper;

    public RoomDeckStore(RoomRepository roomRepository, RoomTitleRepository roomTitleRepository,
                         TitleRepository titleRepository, TitleMapper titleMapper) {
        this.roomRepository = roomRepository;
        this.roomTitleRepository = roomTitleRepository;
        this.titleRepository = titleRepository;
        this.titleMapper = titleMapper;
    }

    @Transactional(readOnly = true)
    public List<Long> getTitleIds(Long roomId) {
        return roomTitleRepository.findAllByRoomIdOrderByPositionAsc(roomId).stream()
                .map(roomTitle -> roomTitle.getTitle().getId())
                .toList();
    }

    @Transactional
    public List<Long> save(Room room, List<TmdbTitle> cards) {
        validateCards(room, cards);
        List<Title> titles = new ArrayList<>();
        for (TmdbTitle card : cards) {
            Title title = titleRepository.findByTmdbIdAndMediaType(card.tmdbId(), card.mediaType())
                    .orElseGet(() -> new Title(card.tmdbId(), card.mediaType(), card.title()));
            titleMapper.update(card, title);
            titles.add(title);
        }
        List<Title> savedTitles = titleRepository.saveAll(titles);

        List<RoomTitle> roomTitles = new ArrayList<>();
        for (int position = 0; position < savedTitles.size(); position++) {
            roomTitles.add(new RoomTitle(room, savedTitles.get(position), position));
        }
        roomTitleRepository.saveAll(roomTitles);
        room.setDeckBuildStatus(DeckBuildStatus.READY);
        roomRepository.save(room);
        return savedTitles.stream().map(Title::getId).toList();
    }

    private void validateCards(Room room, List<TmdbTitle> cards) {
        if (cards.size() != DeckProperties.SIZE
                || cards.stream().map(TmdbTitle::tmdbId).distinct().count() != DeckProperties.SIZE
                || cards.stream().anyMatch(card -> card.mediaType() != room.getContentType())) {
            throw new ConflictException(ErrorCode.NOT_ENOUGH_TITLES,
                    "Expected 20 unique cards of the room's content type");
        }
    }
}
