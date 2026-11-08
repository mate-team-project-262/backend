package com.team262.moviematch.model.history;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArchivedVote {
    @Column(name = "source_participant_id", nullable = false)
    private Long sourceParticipantId;
    @Column(name = "source_card_id", nullable = false)
    private Long sourceCardId;
    @Column(nullable = false)
    private boolean liked;
    public ArchivedVote(Long sourceParticipantId, Long sourceCardId, boolean liked) {
        this.sourceParticipantId = sourceParticipantId;
        this.sourceCardId = sourceCardId;
        this.liked = liked;
    }
}
