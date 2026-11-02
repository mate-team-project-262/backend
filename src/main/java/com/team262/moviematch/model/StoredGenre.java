package com.team262.moviematch.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A genre snapshot supplied with the title details, including its localized name. */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoredGenre {
    @Column(name = "genre_id", nullable = false)
    private Integer id;

    @Column(name = "genre_name", length = 512)
    private String name;

    public StoredGenre(Integer id, String name) {
        this.id = Objects.requireNonNull(id);
        this.name = name;
    }
}
