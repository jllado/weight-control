CREATE TABLE action_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    wording VARCHAR(255) NOT NULL,
    start_date DATE NOT NULL,
    target_days INT NOT NULL,
    first_checkin_at TIMESTAMP(6) NULL,
    stopped_date DATE NULL,
    source_key VARCHAR(120) NULL,
    recommendation_fingerprint VARCHAR(64) NULL,
    source_label VARCHAR(255) NULL,
    restarted_from_id BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_action_attempt_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_action_restart FOREIGN KEY (restarted_from_id) REFERENCES action_attempts(id),
    CONSTRAINT ck_action_target CHECK (target_days > 0),
    INDEX ix_action_user_source (user_id, recommendation_fingerprint)
);
CREATE TABLE action_checkins (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    attempt_id BIGINT NOT NULL,
    checkin_date DATE NOT NULL,
    CONSTRAINT fk_action_checkin_attempt FOREIGN KEY (attempt_id) REFERENCES action_attempts(id),
    CONSTRAINT uq_action_checkin_date UNIQUE (attempt_id, checkin_date)
);
