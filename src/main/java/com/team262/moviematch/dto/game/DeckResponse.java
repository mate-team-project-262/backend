package com.team262.moviematch.dto.game;

import com.team262.moviematch.model.ContentType;
import com.team262.moviematch.model.RoomStatus;
import java.util.List;

public record DeckResponse(String code, RoomStatus status, ContentType contentType,
                           int deckSize, int votedCount, List<DeckCardResponse> cards) {
}
