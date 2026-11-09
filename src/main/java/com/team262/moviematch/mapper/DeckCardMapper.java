package com.team262.moviematch.mapper;

import com.team262.moviematch.dto.game.DeckCardDetails;
import com.team262.moviematch.dto.voc.GenreDto;
import com.team262.moviematch.model.RoomTitle;
import com.team262.moviematch.model.StoredGenre;
import com.team262.moviematch.model.Title;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DeckCardMapper {
    public DeckCardDetails toDetails(RoomTitle card) {
        Title title = card.getTitle();
        List<GenreDto> genres = new ArrayList<>();
        for (StoredGenre genre : title.getGenres()) {
            genres.add(new GenreDto(genre.getId(), genre.getName()));
        }
        genres.sort(Comparator.comparingInt(GenreDto::id));
        Double voteAverage = null;
        if (title.getVoteAverage() != null) {
            voteAverage = title.getVoteAverage().doubleValue();
        }
        return new DeckCardDetails(card.getId(), card.getPosition(), title.getTitle(),
                title.getReleaseYear(), title.getOverview(), posterUrl(title.getPosterPath()),
                title.getRuntimeMinutes(), genres, voteAverage, title.getDirector(),
                List.copyOf(title.getCreators()), List.copyOf(title.getCast()), title.getAgeRating(),
                title.getBudget(), title.getRevenue(), title.getOriginalLanguage());
    }

    private String posterUrl(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        if (path.startsWith("/demo/") || path.startsWith("https://") || path.startsWith("http://")) {
            return path;
        }
        return "https://image.tmdb.org/t/p/w500" + path;
    }
}
