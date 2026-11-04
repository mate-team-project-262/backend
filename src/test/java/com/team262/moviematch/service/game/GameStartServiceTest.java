package com.team262.moviematch.service.game;

import com.team262.moviematch.dto.room.response.StartGameResponse;
import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.ConflictException;
import com.team262.moviematch.exception.ex.ExternalServiceException;
import com.team262.moviematch.model.ContentType;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.model.RoomStatus;
import com.team262.moviematch.service.deck.DeckCandidateSelector;
import com.team262.moviematch.service.deck.TitleStoreService;
import com.team262.moviematch.source.TmdbTitle;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameStartServiceTest {

    @Mock
    GameStartTxService tx;
    @Mock
    DeckCandidateSelector selector;
    @Mock
    TitleStoreService titleStore;
    @InjectMocks
    GameStartService service;
    @Mock
    Participant participant;

    private final StartPlan plan = new StartPlan(1L, ContentType.MOVIES, Set.of(28), 120);

    @Test
    void success_runsStepsInOrderAndReturnsResponse() {
        List<TmdbTitle> cards = List.of();
        List<Long> ids = LongStream.rangeClosed(1, 20).boxed().toList();

        StartGameResponse expected =
                new StartGameResponse("K7M2QX", RoomStatus.VOTING, 20);

        when(tx.claimStart("K7M2QX", participant)).thenReturn(plan);
        when(selector.select(eq(ContentType.MOVIES), any(), any()))
                .thenReturn(cards);
        when(titleStore.storeAll(cards)).thenReturn(ids);
        when(tx.completeStart(1L, ids)).thenReturn(expected);

        assertThat(service.start("K7M2QX", participant)).isSameAs(expected);

        InOrder order = inOrder(tx, selector, titleStore);

        order.verify(tx).claimStart("K7M2QX", participant);
        order.verify(selector).select(eq(ContentType.MOVIES), any(), any());
        order.verify(titleStore).storeAll(cards);
        order.verify(tx).completeStart(1L, ids);

        verify(tx, never()).markBuildFailed(any());
    }

    @Test
    void tmdbFailure_marksFailedAndRethrowsOriginalException() {
        when(tx.claimStart(any(), any())).thenReturn(plan);

        ExternalServiceException boom = new ExternalServiceException();

        when(selector.select(any(), any(), any())).thenThrow(boom);

        assertThatThrownBy(() -> service.start("K7M2QX", participant))
                .isSameAs(boom);

        verify(tx).markBuildFailed(1L);
        verify(titleStore, never()).storeAll(any());
        verify(tx, never()).completeStart(any(), any());
    }

    @Test
    void failureWhileMarkingFailed_doesNotMaskOriginalError() {
        when(tx.claimStart(any(), any())).thenReturn(plan);

        ConflictException original =
                new ConflictException(ErrorCode.NOT_ENOUGH_TITLES, "x");

        when(selector.select(any(), any(), any())).thenThrow(original);

        doThrow(new RuntimeException("db down"))
                .when(tx).markBuildFailed(1L);

        assertThatThrownBy(() -> service.start("K7M2QX", participant))
                .isSameAs(original);

        verify(tx).markBuildFailed(1L);
    }


    @Test
    void claimRejected_doesNotTouchTmdb() {
        when(tx.claimStart(any(), any()))
                .thenThrow(new ConflictException(
                        ErrorCode.ROOM_ALREADY_STARTED, "x"));

        assertThatThrownBy(() -> service.start("K7M2QX", participant))
                .isInstanceOf(ConflictException.class);

        verifyNoInteractions(selector, titleStore);
        verify(tx, never()).markBuildFailed(any());
    }
}