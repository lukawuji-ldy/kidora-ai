package com.wuji.kidora.ai.common.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * JWT 签发与解析（双进程共享同一 secret/issuer）。
 *
 * @author liudy
 */
public class JwtTokenService {

    private final String issuer;
    private final long expireHours;
    private final SecretKey key;

    public JwtTokenService(String secret, String issuer, long expireHours) {
        this.issuer = issuer;
        this.expireHours = expireHours;
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String issueToken(AuthUser user) {
        Instant now = Instant.now();
        Instant exp = now.plus(expireHours, ChronoUnit.HOURS);
        return Jwts.builder()
                .issuer(issuer)
                .subject(user.userId())
                .claim(JwtClaims.USER_ID, user.userId())
                .claim(JwtClaims.USERNAME, user.username())
                .claim(JwtClaims.NICKNAME, user.nickname())
                .claim(JwtClaims.ROLE, user.role())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

    public AuthUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new AuthUser(
                claims.get(JwtClaims.USER_ID, String.class),
                claims.get(JwtClaims.USERNAME, String.class),
                claims.get(JwtClaims.NICKNAME, String.class),
                claims.get(JwtClaims.ROLE, String.class)
        );
    }
}
