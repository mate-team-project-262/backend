package com.team262.moviematch.model.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "archived_participants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArchivedParticipant {
    @Column(name = "user_id")
    @lombok.Setter
    private Long userId;
    @Id
    @Column(name = "source_participant_id", nullable = false)
    private Long sourceParticipantId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private GameSessionArchive session;
    @Column(name = "display_name", nullable = false, length = 20)
    private String displayName;
    @Column(name = "was_host", nullable = false)
    private boolean host;

    public ArchivedParticipant(Long sourceParticipantId, String displayName, boolean host) {
        this.sourceParticipantId = sourceParticipantId;
        this.displayName = displayName;
        this.host = host;
    }

    void attachTo(GameSessionArchive session) {
        this.session = session;
    }
}

