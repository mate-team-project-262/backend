package com.team262.moviematch.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.proxy.HibernateProxy;

@Entity
@Table(name = "titles", uniqueConstraints = @UniqueConstraint(
        name = "uk_titles_tmdb_media", columnNames = {"tmdb_id", "media_type"}))
@Getter
@Setter
public class Title {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    @Column(name = "tmdb_id", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Long tmdbId;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 8, updatable = false)
    @Setter(AccessLevel.NONE)
    private MediaType mediaType;

    @Column(nullable = false, length = 512)
    private String title;

    @Column(name = "release_year")
    private Integer releaseYear;

    @Column(columnDefinition = "TEXT")
    private String overview;

    @Column(name = "poster_path", length = 512)
    private String posterPath;

    @Column(precision = 19, scale = 6)
    private BigDecimal popularity;

    @Column(name = "vote_average", precision = 4, scale = 2)
    private BigDecimal voteAverage;

    @Column(name = "runtime_minutes")
    private Integer runtimeMinutes;

    @ElementCollection
    @CollectionTable(name = "title_genres", joinColumns = @JoinColumn(name = "title_id"))
    @Column(name = "genre_id", nullable = false)
    @Setter(AccessLevel.NONE)
    private Set<Integer> genreIds = new HashSet<>();

    @Column(length = 512)
    private String director;

    @ElementCollection
    @CollectionTable(name = "title_creators", joinColumns = @JoinColumn(name = "title_id"))
    @OrderColumn(name = "position")
    @Column(name = "name", nullable = false, length = 512)
    @Setter(AccessLevel.NONE)
    private List<String> creators = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "title_cast", joinColumns = @JoinColumn(name = "title_id"))
    @OrderColumn(name = "position")
    @Column(name = "name", nullable = false, length = 512)
    @Setter(AccessLevel.NONE)
    private List<String> cast = new ArrayList<>();

    @Column(name = "age_rating", length = 32)
    private String ageRating;

    private Long budget;

    private Long revenue;

    @Column(name = "original_language", length = 16)
    private String originalLanguage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Title() {
    }

    public Title(Long tmdbId, MediaType mediaType, String title) {
        this.tmdbId = tmdbId;
        this.mediaType = mediaType;
        this.title = title;
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || persistentClass(this) != persistentClass(other)) {
            return false;
        }
        Title entity = (Title) other;
        return getId() != null && getId().equals(entity.getId());
    }

    @Override
    public final int hashCode() {
        return persistentClass(this).hashCode();
    }

    @Override
    public final String toString() {
        return "Title{id=" + getId() + "}";
    }

    private static Class<?> persistentClass(Object value) {
        return value instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass() : value.getClass();
    }
}
