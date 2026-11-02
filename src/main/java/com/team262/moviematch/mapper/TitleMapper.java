package com.team262.moviematch.mapper;

import com.team262.moviematch.config.MapperConfig;
import com.team262.moviematch.model.Title;
import com.team262.moviematch.source.TitleGenre;
import com.team262.moviematch.source.TmdbTitle;
import java.util.Collection;
import java.util.stream.Collectors;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(config = MapperConfig.class, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TitleMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tmdbId", ignore = true)
    @Mapping(target = "mediaType", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "genreIds", ignore = true)
    @Mapping(target = "creators", ignore = true)
    @Mapping(target = "cast", ignore = true)
    void update(TmdbTitle source, @MappingTarget Title target);

    @AfterMapping
    default void updateCollections(TmdbTitle source, @MappingTarget Title target) {
        replace(target.getGenreIds(), source.genres().stream().map(TitleGenre::id)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet()));
        replace(target.getCreators(), source.creators());
        replace(target.getCast(), source.cast());
    }

    private static <T> void replace(Collection<T> target, Collection<T> values) {
        if (!target.equals(values)) {
            target.clear();
            target.addAll(values);
        }
    }
}
