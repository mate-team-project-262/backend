package com.team262.moviematch.model.history;

import com.team262.moviematch.model.Title;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArchivedCard {
    @Column(name = "source_card_id", nullable = false)
    private Long sourceCardId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "title_id", nullable = false)
    private Title title;
    @Column(nullable = false)
    private int position;
    @Column(name = "likes_count", nullable = false)
    private int likesCount;
    @Column(nullable = false)
    private boolean winner;

    public ArchivedCard(Long sourceCardId, Title title, int position, int likesCount, boolean winner) {
        this.sourceCardId = sourceCardId;
        this.title = title;
        this.position = position;
        this.likesCount = likesCount;
        this.winner = winner;
    }
}

