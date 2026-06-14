package com.jllado.weightcontrol.security;

import com.jllado.weightcontrol.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtSessionService {

    private final SecretKey secretKey;
    private final AppProperties properties;

    public JwtSessionService(AppProperties properties) {
        this.properties = properties;
        this.secretKey = Keys.hmacShaKeyFor(properties.auth().jwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String createToken(AuthenticatedUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(Long.toString(user.getUserId()))
            .claim("email", user.getEmail())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(properties.auth().jwtExpiration())))
            .signWith(secretKey)
            .compact();
    }

    public AuthenticatedUser parse(String token) {
        Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
        return new AuthenticatedUser(Long.parseLong(claims.getSubject()), claims.get("email", String.class));
    }
}
