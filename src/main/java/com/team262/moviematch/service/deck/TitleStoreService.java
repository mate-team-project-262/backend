package com.team262.moviematch.service.deck;

import com.team262.moviematch.model.Title;
import com.team262.moviematch.repository.TitleRepository;
import com.team262.moviematch.source.TmdbTitle;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TitleStoreService {

    private final TitleRepository titleRepository;
    private final TitleWriter titleWriter;

    public List<Long> storeAll(List<TmdbTitle> titles) {
        return titles.stream().map(this::findOrCreate).toList();
    }

    public Long findOrCreate(TmdbTitle title) {
        return titleRepository.findByTmdbIdAndMediaType(title.tmdbId(), title.mediaType())
                .map(Title::getId)
                .orElseGet(() -> createOrReadWinner(title));
    }

    private Long createOrReadWinner(TmdbTitle title) {
        try {
            return titleWriter.create(title);
        } catch (DataIntegrityViolationException e) {
            return titleRepository.findByTmdbIdAndMediaType(title.tmdbId(), title.mediaType())
                    .map(Title::getId)
                    .orElseThrow(() -> e);
        }
    }
}
