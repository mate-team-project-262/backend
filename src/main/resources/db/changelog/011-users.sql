--liquibase formatted sql
--changeset team262:011-users
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
);
ALTER TABLE users ADD CONSTRAINT uk_users_display_name UNIQUE (display_name);
ALTER TABLE participants ADD COLUMN user_id BIGINT NULL;
ALTER TABLE participants ADD CONSTRAINT fk_participant_user FOREIGN KEY (user_id) REFERENCES users(id);
CREATE INDEX idx_participants_user ON participants(user_id);
--rollback ALTER TABLE participants DROP FOREIGN KEY fk_participant_user;
--rollback ALTER TABLE participants DROP COLUMN user_id;
--rollback DROP TABLE users;

