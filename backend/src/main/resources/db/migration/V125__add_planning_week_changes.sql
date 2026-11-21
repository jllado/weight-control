CREATE TABLE planning_week_changes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    effective_date DATE NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_planning_week_changes_user_date UNIQUE (user_id, effective_date),
    CONSTRAINT fk_planning_week_changes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
