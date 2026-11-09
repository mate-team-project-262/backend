package com.team262.moviematch.controller;

import com.team262.moviematch.config.OpenApiConfig;
import com.team262.moviematch.dto.game.DeckResponse;
import com.team262.moviematch.dto.game.VoteRequest;
import com.team262.moviematch.dto.game.VoteResponse;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.security.CurrentParticipant;
import com.team262.moviematch.service.game.DeckReadService;
import com.team262.moviematch.service.game.VoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rooms/{code}")
@RequiredArgsConstructor
@Tag(name = "Deck", description = "Saved cards, details and personal votes")
@SecurityRequirement(name = OpenApiConfig.PARTICIPANT_TOKEN)
public class DeckController {
    private final DeckReadService deckService;
    private final VoteService voteService;

    @GetMapping("/deck")
    @Operation(operationId = "getDeck", summary = "Read the saved deck and personal votes",
            description = "Available after game start. Includes More details; never calls TMDB. "
                    + "myVote is true for a like, false for a skip and null for an unanswered card.")
    @ApiResponse(responseCode = "200", description = "Saved deck in position order")
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN")
    @ApiResponse(responseCode = "403", description = "WRONG_ROOM")
    @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "ROOM_NOT_VOTING: game has not started")
    public DeckResponse getDeck(@PathVariable String code,
            @Parameter(hidden = true) @CurrentParticipant Participant participant) {
        return deckService.getDeck(code, participant);
    }

    @PostMapping("/votes")
    @Operation(operationId = "saveVote", summary = "Save a like or skip",
            description = "New votes are accepted in VOTING. Repeating the same choice succeeds "
                    + "even after the round finishes; changing it returns ALREADY_VOTED.")
    @ApiResponse(responseCode = "200", description = "Personal progress after saving")
    @ApiResponse(responseCode = "400", description = "Invalid request or TITLE_NOT_IN_DECK")
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN")
    @ApiResponse(responseCode = "403", description = "WRONG_ROOM")
    @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "ROOM_NOT_VOTING or ALREADY_VOTED")
    public VoteResponse vote(@PathVariable String code,
            @Parameter(hidden = true) @CurrentParticipant Participant participant,
            @Valid @RequestBody VoteRequest request) {
        return voteService.vote(code, participant, request);
    }
}
