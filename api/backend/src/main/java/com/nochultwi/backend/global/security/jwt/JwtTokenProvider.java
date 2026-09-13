package com.nochultwi.backend.global.security.jwt;

import com.nochultwi.backend.domain.user.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {

    private final Key key;
    private final long validityInMilliseconds;

    public JwtTokenProvider(
            @Value("${jwt.secret:c3ByaW5nLWJvb3Qtc2VjdXJpdHktand0LXNlY3JldC1rZXktZXhhbXBsZS1ub2NodWx0d2ktMjAyNC1zb21ldGhpbmctc2VjdXJl}") String secretKey,
            @Value("${jwt.expiration:86400000}") long validityInMilliseconds) {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.validityInMilliseconds = validityInMilliseconds;
    }

    /**
     * 토큰 생성 (로그인 시 사용)
     */
    public String createToken(String loginId, Role role) {
        Claims claims = Jwts.claims().setSubject(loginId);
        claims.put("role", role != null ? role.name() : Role.ROLE_STUDENT.name());

        Date now = new Date();
        Date validity = new Date(now.getTime() + validityInMilliseconds);

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 토큰에서 DB 조회 없이 인증(Authentication) 객체 생성
     */
    public Authentication getAuthentication(String token) {
        Claims claims = parseClaims(token);
        String loginId = claims.getSubject();
        String role = claims.get("role", String.class);

        Collection<? extends GrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority(role != null ? role : "ROLE_STUDENT")
        );

        // principal 자리에 loginId(String)를 직접 보관
        return new UsernamePasswordAuthenticationToken(loginId, null, authorities);
    }

    /**
     * 토큰에서 유저 loginId 추출
     */
    public String getLoginId(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * 토큰 유효성 및 만료 여부 검증
     */
    public boolean validateToken(String token) {
        try {
            Jws<Claims> claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
            return !claims.getBody().getExpiration().before(new Date());
        } catch (SecurityException | MalformedJwtException e) {
            log.warn("잘못된 JWT 서명입니다: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.warn("만료된 JWT 토큰입니다: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("지원되지 않는 JWT 토큰입니다: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT 토큰이 비어있거나 잘못되었습니다: {}", e.getMessage());
        }
        return false;
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
