package com.kologeski.interviewassistant.auth;

import com.kologeski.interviewassistant.user.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.time.*;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey key;
    private final Duration expiration;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.access-expiration}") Duration expiration) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expiration = expiration;
    }

    public String generate(User user) {
        Instant now = Instant.now();
        return Jwts.builder().subject(user.getEmail())
                .claim("userId", user.getId()).claim("name", user.getName())
                .issuedAt(Date.from(now)).expiration(Date.from(now.plus(expiration)))
                .signWith(key).compact();
    }

    public String subject(String token) { return claims(token).getSubject(); }
    public boolean isValid(String token, String email) {
        Claims claims = claims(token);
        return email.equals(claims.getSubject()) && claims.getExpiration().after(new Date());
    }
    public long expirationSeconds() { return expiration.toSeconds(); }
    private Claims claims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
