package com.team262.moviematch.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.proxy.HibernateProxy;

@Entity
@Table(name = "participants")
public class Participant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(nullable = false, length = 20)
    private String name;

    @Column(name = "is_host", nullable = false)
    private boolean host;

    @Column(nullable = false, unique = true, length = 36)
    private String token;

    @Column(name = "token_created_at", nullable = false)
    private LocalDateTime tokenCreatedAt;

    protected Participant() {
    }

    public Participant(Room room, String name, boolean host, String token,
                       LocalDateTime tokenCreatedAt) {
        this.room = room;
        this.name = name;
        this.host = host;
        this.token = token;
        this.tokenCreatedAt = tokenCreatedAt;
    }

    public Long getId() {
        return id;
    }

    public Room getRoom() {
        return room;
    }

    public void setRoom(Room room) {
        this.room = room;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isHost() {
        return host;
    }

    public void setHost(boolean host) {
        this.host = host;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public LocalDateTime getTokenCreatedAt() {
        return tokenCreatedAt;
    }

    public void setTokenCreatedAt(LocalDateTime tokenCreatedAt) {
        this.tokenCreatedAt = tokenCreatedAt;
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || persistentClass(this) != persistentClass(other)) {
            return false;
        }
        Participant participant = (Participant) other;
        return getId() != null && getId().equals(participant.getId());
    }

    @Override
    public final int hashCode() {
        return persistentClass(this).hashCode();
    }

    @Override
    public final String toString() {
        return "Participant{id=" + getId() + "}";
    }

    private static Class<?> persistentClass(Object value) {
        return value instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass() : value.getClass();
    }
}