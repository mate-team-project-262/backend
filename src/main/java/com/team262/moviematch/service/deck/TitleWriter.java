package com.team262.moviematch.service.deck;

import com.team262.moviematch.mapper.TitleMapper;
import com.team262.moviematch.model.Title;
import com.team262.moviematch.repository.TitleRepository;
import com.team262.moviematch.source.TmdbTitle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TitleWriter {

    private final TitleRepository titleRepository;
    private final TitleMapper titleMapper;

    /** A custom, short-lived transaction. Uniqueness violations are excluded from this method. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long create(TmdbTitle source) {
        Title title = new Title(source.tmdbId(), source.mediaType(), source.title());
        titleMapper.update(source, title);
        return titleRepository.saveAndFlush(title).getId();
    }
}
