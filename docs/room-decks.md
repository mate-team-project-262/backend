# Room deck preparation

`RoomDeckService.prepare(code, participantId)` returns 20 local title IDs in the
room's saved order. An overload accepts a room ID. `getTitleIds(code, participantId)`
returns the same order to participants. These are internal service methods;
HTTP endpoints and the transition from LOBBY to VOTING are separate tasks.

## Service flow

1. Find the room through `RoomRepository`; a missing room produces `ROOM_NOT_FOUND`.
2. Check that the participant belongs to the room and is the host.
3. Read saved `RoomTitle` entries in position order. Return their IDs when a deck exists.
4. For an empty deck, check that the room is in LOBBY and create `DiscoverFilters`.
5. Ask the selector for 20 unique cards of the requested content type.
6. `RoomDeckStore` looks up each title by TMDB ID and content type, reuses existing
   titles, maps the details and calls `TitleRepository.saveAll()`.
7. Create positions 0-19 and call `RoomTitleRepository.saveAll()`.
8. Set `room.deckBuildStatus` to READY and save the room.

The public service methods use ordinary Spring `@Transactional` annotations.
Selection, title saving, room positions and the READY update are one synchronous
operation. A source or database failure rolls back every database change made by
that operation. The room remains available for another attempt.

This simple version keeps a database transaction open during TMDB calls. It has
no build tokens, leases, background threads, manual locks or automatic retries.
Concurrent preparation requests can both call TMDB; database unique constraints
reject duplicate title identities or room positions, and a losing request can
fail. Sequential repeat requests reuse the stored deck without new TMDB calls.

## Selection

Discover page 1 supplies the total page and result counts. The selector chooses
up to four distinct random pages within the configured window and initially
samples roughly five candidates from each. It reuses page 1 when selected.
Duplicate candidates and candidates without a poster or description are skipped.
Details are loaded sequentially; unsuitable details are replaced from the reserve
or additional pages. The final 20 cards are shuffled before saving.

Empty genre and duration preferences impose no corresponding filter. Requested
genres and duration are checked against the full details. Missing runtime cannot
satisfy an explicit maximum duration. Both movies and TV series are supported.

| Setting | Default | Purpose |
| --- | --- | --- |
| `DECK_MAX_PAGE_WINDOW` | 50 | Window of pages to sample |
| `DECK_INITIAL_PAGE_COUNT` | 4 | Initial page diversity |
| `DECK_MAX_DISCOVER_REQUESTS` | 8 | Maximum page requests |
| `DECK_MAX_DETAILS_REQUESTS` | 30 | Maximum detail requests |
| `TMDB_MAX_RESPONSE_BYTES` | 4194304 | Maximum HTTP response body |

TMDB connection and read timeouts remain 3s and 5s by default. There is no separate
overall deck deadline. The request limits prevent unbounded candidate searching.

## Errors

- `404 ROOM_NOT_FOUND`: the room does not exist.
- `403 WRONG_ROOM` or `NOT_HOST`: the participant cannot prepare this room.
- `409 ROOM_NOT_JOINABLE`: an empty deck cannot be prepared outside LOBBY.
- `409 RESULT_NOT_READY`: a participant reads an empty deck.
- `409 NOT_ENOUGH_TITLES`: Discover reports too few results or the sampled catalog
  is exhausted without 20 suitable cards.
- `503 DECK_SELECTION_LIMIT`: the configured request limit was reached.
- `502 TMDB_UNAVAILABLE`: a TMDB request or response failed.

No partial deck or automatic fallback to stub is returned.

## Database compatibility and tests

Existing Liquibase migrations are retained so databases that already applied them
continue to start. Legacy build-token and expiry columns are unused by the Java
model. The deck status retains the existing `deck_build_status` column; READY is
the deck preparation status, while the room's game status stays LOBBY.

Run `mvn verify` with Docker for unit and MySQL integration tests. Tests cover
saved order, reuse between participants and rooms, permissions, missing rooms,
rollback after source/database errors, both content types and candidate filtering.
Live TMDB tests remain opt-in through `TMDB_LIVE_TESTS` and `DECK_LIVE_TESTS`.
