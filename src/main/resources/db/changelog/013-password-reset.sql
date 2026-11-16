--liquibase formatted sql
--changeset team262:013-password-reset
ALTER TABLE users ADD COLUMN session_version BIGINT NOT NULL DEFAULT 0;
CREATE TABLE password_reset_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_password_reset_token_hash UNIQUE (token_hash),
    CONSTRAINT uk_password_reset_user UNIQUE (user_id),
    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
--rollback DROP TABLE password_reset_tokens;
--rollback ALTER TABLE users DROP COLUMN session_version;
