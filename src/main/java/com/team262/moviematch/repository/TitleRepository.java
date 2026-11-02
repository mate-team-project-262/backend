package com.team262.moviematch.repository;

import com.team262.moviematch.model.ContentType;
import com.team262.moviematch.model.Title;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface TitleRepository extends JpaRepository<Title, Long> {
    Optional<Title> findByTmdbIdAndMediaType(Long tmdbId, ContentType mediaType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Title t where t.tmdbId = :tmdbId and t.mediaType = :mediaType")
    Optional<Title> findForUpdate(@Param("tmdbId") Long tmdbId, @Param("mediaType") ContentType mediaType);
}
