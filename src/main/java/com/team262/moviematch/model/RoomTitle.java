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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.proxy.HibernateProxy;

@Entity
@Table(name = "room_titles", uniqueConstraints = {
        @UniqueConstraint(name = "uk_room_titles_position", columnNames = {"room_id", "position"}),
        @UniqueConstraint(name = "uk_room_titles_title", columnNames = {"room_id", "title_id"})
})
@Getter
@Setter
public class RoomTitle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "title_id", nullable = false)
    private Title title;

    @Column(nullable = false)
    private int position;

    protected RoomTitle() {
    }

    public RoomTitle(Room room, Title title, int position) {
        this.room = room;
        this.title = title;
        this.position = position;
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || persistentClass(this) != persistentClass(other)) {
            return false;
        }
        RoomTitle entity = (RoomTitle) other;
        return getId() != null && getId().equals(entity.getId());
    }

    @Override
    public final int hashCode() {
        return persistentClass(this).hashCode();
    }

    @Override
    public final String toString() {
        return "RoomTitle{id=" + getId() + "}";
    }

    private static Class<?> persistentClass(Object value) {
        return value instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass() : value.getClass();
    }
}
