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
- POST /auth/forgot-password: {email}; 202 with a generic message for known and unknown
  addresses. Requires a CSRF token and cookie from GET /auth/csrf. A new request
  replaces the previous reset link for that account.
- GET /auth/reset-password/check?token=...: 204 for a valid link, 400
  RESET_TOKEN_INVALID otherwise. The link is not consumed.
- POST /auth/reset-password: {token, newPassword}; 204. Requires CSRF but no account
  session or current password. The link expires after 20 minutes and is usable once.
  On success all account sessions become invalid; sign in using the new password.
  Active participant tokens remain valid independently.
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

Reset links are sent to the registered email via SMTP and include the current
sign-in name. Configure FRONTEND_URL (HTTPS in production), MAIL_HOST, MAIL_PORT,
MAIL_USERNAME, MAIL_PASSWORD, MAIL_FROM, MAIL_SMTP_AUTH, MAIL_STARTTLS_ENABLED and
MAIL_SSL_ENABLED. Do not log reset links, tokens, or SMTP credentials. The
application does not provision a mail server.

Forgot-password is limited to one request per email per minute and additionally
per IP. Check and reset endpoints are limited per IP. A 429 TOO_MANY_REQUESTS
includes Retry-After in seconds. Limits are held in memory of one application
instance and reset on restart; deployments with multiple instances require a
shared rate limit store. The frontend should remove the token from the address bar and avoid
sending it to analytics. The reverse proxy must omit token query parameters
from access logs. Ordinary password change, email update, and account deletion
cancel outstanding reset links.

Social login, actor images and recommendations are separate follow-up features.
