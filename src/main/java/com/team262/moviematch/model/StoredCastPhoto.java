package com.team262.moviematch.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoredCastPhoto {
    @Column(name = "person_id")
    private Long personId;

    @Column(name = "profile_path", length = 512)
    private String profilePath;

    public StoredCastPhoto(Long personId, String profilePath) {
        this.personId = personId;
        this.profilePath = profilePath;
    }
}
