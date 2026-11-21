CREATE TABLE overnight_heart_rate_limits (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    effective_from DATE NOT NULL,
    bpm DECIMAL(7,3) NOT NULL,
    CONSTRAINT uq_overnight_hr_limit_date UNIQUE (user_id, effective_from),
    CONSTRAINT fk_overnight_hr_limit_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- The owner's accepted October 23–November 21 median was 65.5 bpm.
INSERT INTO overnight_heart_rate_limits (user_id, effective_from, bpm)
SELECT id, '2026-11-21', 70.5 FROM users WHERE email = 'jllado@gmail.com';

-- For each other account, use the two middle rates among its first 30 valid nights.
INSERT INTO overnight_heart_rate_limits (user_id, effective_from, bpm)
WITH dated AS (
    SELECT s.user_id, s.sleep_date, s.average_heart_rate,
           ROW_NUMBER() OVER (PARTITION BY s.user_id ORDER BY s.sleep_date) AS night_number
    FROM sleeps s
    JOIN users u ON u.id = s.user_id
    WHERE s.average_heart_rate > 0 AND u.email <> 'jllado@gmail.com'
), ranked AS (
    SELECT user_id, sleep_date, average_heart_rate,
           ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY average_heart_rate, sleep_date) AS rate_number
    FROM dated WHERE night_number <= 30
)
SELECT user_id, MAX(sleep_date), AVG(CASE WHEN rate_number IN (15, 16) THEN average_heart_rate END) + 5
FROM ranked GROUP BY user_id HAVING COUNT(*) = 30;
