# Sprint 1 frontend API guide

Local backend: `http://localhost:8080`. Swagger UI: `/swagger-ui/index.html`.
Contracts: `/v3/api-docs` (JSON) and `/v3/api-docs.yaml` (YAML).
This guide describes implemented behavior rather than earlier Miro examples.

## Workflow

1. The host sends `POST /rooms` and saves `code` and **`participant.token`**.
2. The frontend builds an invitation URL `/join/{code}` on its own domain.
3. A guest enters a display name and sends `POST /rooms/{code}/join`.
   Save the guest's own token from **`currentParticipant.token`**.
4. Each user calls `GET /rooms/{code}` with their token in **`X-Participant-Token`**.
   Display the returned preferences and participants. Poll every 3 seconds, for example;
   WebSocket updates are not available.

Lobby access before creating or joining a room is not supported. Do not send
`CurrentParticipant` in the request body: the server resolves it from the header.
`Authorization: Bearer` is not used. Tokens expire 24 hours after issuance;
GET does not refresh them. Never share your token. The shared participant list
contains no tokens.

## Create a room

```http
POST /rooms
Content-Type: application/json

{"displayName":"Anna","preferences":{"contentType":"MOVIES","genreIds":[28,35],"maxDurationMinutes":120}}
```

Success: `201 Created`, with the `Location: /rooms/{code}` header.

```json
{
  "code": "K7M2QX", "status": "LOBBY",
  "preferences": {"contentType": "MOVIES", "genreIds": [28,35], "maxDurationMinutes": 120},
  "participant": {
    "id": 1, "displayName": "Anna", "host": true,
    "token": "3f2b8c1e-5a4d-4e7b-9c2a-1d6f0b7e8a90"
  }
}
```

`displayName` is required, non-blank and 3-20 characters long. Omit `preferences`
or send `null` to use `MOVIES`, `genreIds: []` and `maxDurationMinutes: null`.
If preferences are supplied, `contentType` is required: `MOVIES` or `TV_SERIES`.
Omitted, `null` or empty genres mean any genre. Null elements are rejected,
duplicates are removed and order is not guaranteed. Genre names are not returned.
Duration accepts **any integer from 30 through 150 inclusive, or null**;
omitting it also means no limit. The 90/120/150 buttons are UI choices, not an API enum.

## Join a room

```http
POST /rooms/K7M2QX/join
Content-Type: application/json

{"displayName":"Egor"}
```

Success: `201 Created`.

```json
{
  "code": "K7M2QX", "status": "LOBBY",
  "currentParticipant": {
    "id": 2, "displayName": "Egor", "host": false,
    "token": "7c1487a2-c472-4482-b426-e361409028e7"
  }
}
```

The room must exist, be in `LOBBY` status and have fewer than 5 participants,
including the host. The display name must be available in that room,
case-insensitively (`Anna` and `anna` conflict). Joining does not restore a previous
session: retrying with a taken name returns `NAME_TAKEN`.

## Get the lobby

```http
GET /rooms/K7M2QX
X-Participant-Token: 7c1487a2-c472-4482-b426-e361409028e7
```

Success: `200 OK`.

```json
{
  "code": "K7M2QX", "status": "LOBBY",
  "preferences": {"contentType": "MOVIES", "genreIds": [28,35], "maxDurationMinutes": 120},
  "participantCount": 2, "maxParticipants": 5,
  "participants": [
    {"id": 1, "displayName": "Anna", "host": true},
    {"id": 2, "displayName": "Egor", "host": false}
  ]
}
```

Participants are ordered by ID. `participantCount` includes the host. GET also
returns `VOTING`/`FINISHED`; this API does not implement transitions between statuses.
Room codes are case-insensitive. Lobby responses contain no `roomId`, participant
tokens or token issuance timestamps.

## Errors

Business errors and Bean Validation failures use this format:

```json
{"code":"NAME_TAKEN","message":"Name already has taken","fieldErrors":[]}
```

```json
{
  "code": "VALIDATION_ERROR", "message": "Validation failed",
  "fieldErrors": [{"field":"preferences.maxDurationMinutes","message":"must be less than or equal to 150"}]
}
```

Handle errors by HTTP status and `code`, not by `message` text.
`fieldErrors` contains request field paths; it is empty for business errors.

| Operation | HTTP | code | Cause / frontend action |
| --- | --- | --- | --- |
| Create, join | 400 | `VALIDATION_ERROR` | Check form fields and display `fieldErrors` |
| GET | 401 | `INVALID_TOKEN` | Missing, blank, unknown or expired token; protected lobby access is unavailable |
| GET | 403 | `WRONG_ROOM` | Token belongs to another room; use the correct room's token |
| GET, join | 404 | `ROOM_NOT_FOUND` | Room does not exist; display an invitation code error |
| Join | 409 | `ROOM_NOT_JOINABLE` | Status is no longer `LOBBY`; joining is closed |
| Join | 409 | `ROOM_FULL` | Room already has 5 participants |
| Join | 409 | `NAME_TAKEN` | Name is taken; ask for another one |
| Create, join | 400 | not provided | Malformed JSON, wrong type, unknown enum or missing body |
| Create, join | 415 | not provided | Unsupported Content-Type; send JSON |
| Create | 503 | not provided | No unique room code after 5 attempts; creation can be retried |
| All | 500 | not provided | Technical failure; for join, includes token generation retry exhaustion |

HTTP/JSON errors and technical failures use Spring's default error format:

```json
{"status":400,"error":"Bad Request","path":"/rooms"}
```

It may also include `timestamp`; `code` and `fieldErrors` are not guaranteed.
GET checks: token validity, room existence, room membership.
Join checks: room existence, status, capacity, name availability.
If multiple conditions fail, the first detected error is returned.

Do not automatically retry POST after a timeout or network error: the operation
may have succeeded while its response was lost. A repeated join creates another
participant or conflicts on the name; idempotency keys are not supported.

## Swagger and frontend connectivity

Click **Authorize**, paste the token UUID without `Bearer`, then call GET.
Create and join do not require authorization. Documentation tokens are fictitious;
use the token from your own successful POST for **Try it out**.

Swagger uses the backend's origin. A frontend on another port/domain needs a
dev/reverse proxy or an agreed CORS configuration allowing the frontend origin
and headers (`Content-Type`, `X-Participant-Token`). The backend does not configure CORS.
