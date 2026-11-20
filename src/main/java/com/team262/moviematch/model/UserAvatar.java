package com.team262.moviematch.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_avatars")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAvatar {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "content_type", nullable = false, length = 32)
    private String contentType;

    @Lob
    @Column(name = "image_bytes", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] imageBytes;

    public UserAvatar(Long userId, String contentType, byte[] imageBytes) {
        this.userId = userId;
        this.contentType = contentType;
        this.imageBytes = imageBytes.clone();
    }

    public void replace(String contentType, byte[] imageBytes) {
        this.contentType = contentType;
        this.imageBytes = imageBytes.clone();
    }
}
