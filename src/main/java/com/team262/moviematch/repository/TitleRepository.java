package com.team262.moviematch.repository;

import com.team262.moviematch.model.ContentType;
import com.team262.moviematch.model.Title;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TitleRepository extends JpaRepository<Title, Long> {
    Optional<Title> findByTmdbIdAndMediaType(Long tmdbId, ContentType mediaType);
}
