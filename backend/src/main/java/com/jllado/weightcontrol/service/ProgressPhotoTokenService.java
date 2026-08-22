package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.domain.ProgressPhotoSide;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProgressPhotoTokenService {

    static final String PURPOSE = "progress-photo";
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(5);

    private final SecretKey secretKey;
    private final Clock clock;

    @Autowired
    public ProgressPhotoTokenService(AppProperties properties) {
        this(properties, Clock.systemUTC());
    }

    ProgressPhotoTokenService(AppProperties properties, Clock clock) {
        this.secretKey = Keys.hmacShaKeyFor(
            properties.chatGptActions().fileSigningSecret().getBytes(StandardCharsets.UTF_8)
        );
        this.clock = clock;
    }

    public String create(long userId, long photoSetId, ProgressPhotoSide side) {
        return create(userId, photoSetId, side, PURPOSE);
    }

    String create(long userId, long photoSetId, ProgressPhotoSide side, String purpose) {
        Instant now = clock.instant();
        return Jwts.builder()
            .subject(Long.toString(userId))
            .claim("photoSetId", photoSetId)
            .claim("side", side.name())
            .claim("purpose", purpose)
            .expiration(Date.from(now.plus(TOKEN_LIFETIME)))
            .signWith(secretKey)
            .compact();
    }

    public ProgressPhotoToken parse(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(secretKey)
            .clock(() -> Date.from(clock.instant()))
            .build()
            .parseSignedClaims(token)
            .getPayload();
        if (!PURPOSE.equals(claims.get("purpose", String.class))) {
            throw new NotFoundException("Progress photo not found");
        }
        return new ProgressPhotoToken(
            Long.parseLong(claims.getSubject()),
            ((Number) claims.get("photoSetId")).longValue(),
            ProgressPhotoSide.valueOf(claims.get("side", String.class))
        );
    }

    public record ProgressPhotoToken(long userId, long photoSetId, ProgressPhotoSide side) {
    }
}
