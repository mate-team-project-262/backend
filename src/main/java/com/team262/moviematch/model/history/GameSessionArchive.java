package com.team262.moviematch.model.history;

import com.team262.moviematch.model.ContentType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "game_session_archives")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameSessionArchive {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "source_room_id", nullable = false, unique = true)
    private Long sourceRoomId;
    @Column(name = "room_code", nullable = false, length = 6)
    private String roomCode;
    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 16)
    private ContentType contentType;
    @Column(name = "started_at")
    private Instant startedAt;
    @Column(name = "finished_at", nullable = false)
    private Instant finishedAt;
    @Column(name = "participant_count", nullable = false)
    private int participantCount;
    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ArchivedParticipant> participants = new ArrayList<>();
    @ElementCollection
    @CollectionTable(name = "archived_cards", joinColumns = @JoinColumn(name = "session_id"))
    @OrderBy("position ASC")
    private List<ArchivedCard> cards = new ArrayList<>();
    @ElementCollection
    @CollectionTable(name = "archived_votes", joinColumns = @JoinColumn(name = "session_id"))
    private List<ArchivedVote> votes = new ArrayList<>();

    public GameSessionArchive(Long sourceRoomId, String roomCode, ContentType contentType,
                              Instant startedAt, Instant finishedAt,
                              List<ArchivedParticipant> participants, List<ArchivedCard> cards) {
        this.sourceRoomId = sourceRoomId;
        this.roomCode = roomCode;
        this.contentType = contentType;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.participantCount = participants.size();
        participants.forEach(participant -> participant.attachTo(this));
        this.participants.addAll(participants);
        this.cards.addAll(cards);
    }
}

