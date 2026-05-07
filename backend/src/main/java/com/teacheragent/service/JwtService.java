package com.teacheragent.service;

import com.teacheragent.common.CurrentUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expire-hours:168}")
    private int expireHours;

    private SecretKey key;

    @PostConstruct
    public void init() {
        // 至少 32 字节
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 0, bytes.length);
            bytes = padded;
        }
        key = Keys.hmacShaKeyFor(bytes);
    }

    public String generate(Long userId, String username, String role) {
        long now = System.currentTimeMillis();
        long exp = now + (long) expireHours * 3600 * 1000;
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .issuedAt(new Date(now))
                .expiration(new Date(exp))
                .signWith(key)
                .compact();
    }

    public CurrentUser parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            CurrentUser u = new CurrentUser();
            u.setId(Long.valueOf(claims.getSubject()));
            u.setUsername(claims.get("username", String.class));
            u.setRole(claims.get("role", String.class));
            return u;
        } catch (Exception e) {
            log.debug("JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }
}
