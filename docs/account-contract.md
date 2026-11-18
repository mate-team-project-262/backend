# Account API

Proposed implementation contract. Sign in uses the unique display name shown in the design.

- Email: trimmed, lowercase, valid address, maximum 254 characters.
- Display name: 3–20 characters after trimming, unique ignoring case.
- Password: 8–72 characters, includes a letter and a number; never trimmed.
- GET /auth/csrf: returns token and headerName. Send the token in that header for
  every state-changing request under /auth and /me (POST, PUT, PATCH and DELETE),
  with credentials included.
- POST /auth/register: {email, displayName, password}; 201 with {id, email, displayName}
  and a session cookie.
- POST /auth/login: {displayName, password}; 200 with public user data and a session cookie.
- POST /auth/logout: 204; invalidates session.
- GET /me: 200 with public user data, or 401.
- PATCH /me: updates displayName and/or email. Changing the email requires
  currentPassword. Returns the updated public user data.
- PUT /me/password: {currentPassword, newPassword}; 204, saves the new password
  and invalidates the current account session.
- DELETE /me: {currentPassword}; 204, permanently deletes the account and
  invalidates the current account session. Active participants become guests;
  shared finished-session archives remain available to other accounts.
- POST /me/participants/link: X-Participant-Token identifies the active guest.
  Requires account authentication and CSRF. Returns 204.

Session inactivity timeout: 24 hours. Cookie is HttpOnly, SameSite=Lax, Secure
in HTTPS deployments. Allowed frontend origins must be configured explicitly.
Refresh CSRF via GET /auth/csrf after registration, login, or logout.

Guest game endpoints continue using X-Participant-Token. Linking is explicit;
email, name, or a caller-supplied user ID never establish ownership.
The participant may be linked during the game or on the results screen while its
24-hour participant token is valid. Leaving first revokes that token and skips saving personal history.

Errors use {code, message, fieldErrors}. Duplicate email: 409 EMAIL_TAKEN.
Wrong name/password: 401 INVALID_CREDENTIALS with the same message.
Missing account session: 401 AUTHENTICATION_REQUIRED. Invalid CSRF: 403.
Credentials are checked by Spring Security's DaoAuthenticationProvider.

GET /me/history summaries include winnerTitles with likesCount, previewTitles
(the first three cards), totalCards and title genres. GET /me/history/{sessionId}
returns all cards with their genres and reactions.

Password reset, social login, actor images and recommendations are separate
follow-up features.
