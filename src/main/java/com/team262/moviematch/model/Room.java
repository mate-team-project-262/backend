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
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.hibernate.proxy.HibernateProxy;

@Entity
@Table(name = "rooms")
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 6)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RoomStatus status = RoomStatus.LOBBY;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 16)
    private ContentType contentType = ContentType.MOVIES;

    @Column(name = "max_duration_minutes")
    private Integer maxDurationMinutes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @ElementCollection
    @CollectionTable(name = "room_genres", joinColumns = @JoinColumn(name = "room_id"))
    @Column(name = "genre_id", nullable = false)
    private Set<Integer> genreIds = new HashSet<>();

    protected Room() {
    }

    public Room(String code) {
        setCode(code);
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code == null ? null : code.toUpperCase(Locale.ROOT);
    }

    public RoomStatus getStatus() {
        return status;
    }

    public void setStatus(RoomStatus status) {
        this.status = status;
    }

    public ContentType getContentType() {
        return contentType;
    }

    public void setContentType(ContentType contentType) {
        this.contentType = contentType;
    }

    public Integer getMaxDurationMinutes() {
        return maxDurationMinutes;
    }

    public void setMaxDurationMinutes(Integer maxDurationMinutes) {
        this.maxDurationMinutes = maxDurationMinutes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Set<Integer> getGenreIds() {
        return genreIds;
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || persistentClass(this) != persistentClass(other)) {
            return false;
        }
        Room room = (Room) other;
        return getId() != null && getId().equals(room.getId());
    }

    @Override
    public final int hashCode() {
        return persistentClass(this).hashCode();
    }

    @Override
    public final String toString() {
        return "Room{id=" + getId() + "}";
    }

    private static Class<?> persistentClass(Object value) {
        return value instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass() : value.getClass();
    }
}
