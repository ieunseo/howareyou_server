package org.example.howareyou.global.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secret;

    // 액세스 토큰: 10분, 리프레시 토큰: 14일
    private static final long ACCESS_EXP_MS = 1_000 * 60 * 10; // 10분
    private static final long REFRESH_EXP_MS = 1_000L * 60 * 60 * 24 * 14; // 14일


    public String access(String sub) { return build(sub, ACCESS_EXP_MS); }
    public String refresh(String sub) { return build(sub, REFRESH_EXP_MS); }
    /**
     * 시크릿 문자열을 기반으로 서명 검증에 사용할 HMAC SHA256 키를 생성합니다.
     */
    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private String build(String sub, long expMs) {
        return Jwts.builder()
                .setSubject(sub)
                .setId(UUID.randomUUID().toString())
                .setExpiration(Date.from(Instant.now().plusMillis(expMs)))
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * JWT 토큰을 파싱하여 Claims(사용자 정보, 만료 정보 등)를 추출합니다.
     * - 서명 검증 및 구조 검증을 수행합니다.
     * - 주로 사용자 ID 추출이 필요할 때 사용합니다.
     *
     * @param token 클라이언트가 전달한 JWT
     * @return 토큰에서 추출한 Claims (subject, exp 등 포함)
     * @throws io.jsonwebtoken.JwtException 서명 위조, 만료 등 유효하지 않을 경우 예외 발생
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .setSigningKey(key())                  // secretKey 직접 전달
                .parseClaimsJws(token)                 // 서명 확인 + 전체 JWS 파싱
                .getBody();                            // Claims 반환
    }

    /**
     * JWT 토큰에서 사용자 ID를 추출합니다.
     * @param token JWT 토큰 문자열
     * @return 사용자 ID (String)
     * @throws io.jsonwebtoken.JwtException 토큰이 유효하지 않은 경우
     */
    public String getMemberIdFromToken(String token) {
        return parse(token).getSubject();
    }

    /**
     * Access Token에서 사용자 식별자를 추출합니다.
     * @param token Access Token
     * @return 사용자 식별자 (membername)
     */
    public String getIdentifierFromAccessToken(String token) {
        Claims claims = parse(token);
        String identifier = claims.get("identifier", String.class);
        
        if (identifier == null || identifier.trim().isEmpty()) {
            // identifier가 없으면 subject를 사용 (하위 호환성)
            String subject = claims.getSubject();
            if ("access_token".equals(subject)) {
                throw new IllegalArgumentException("Access token does not contain valid identifier");
            }
            return subject;
        }
        
        return identifier;
    }

    /**
     * Access Token에서 Auth ID를 추출합니다.
     * @param token Access Token
     * @return Auth 테이블 ID
     */
    public Long getAuthIdFromAccessToken(String token) {
        return extractAuthId(parse(token));
    }

    /**
     * 만료된 Access Token에서도 Auth ID를 꺼냅니다. (토큰 갱신용)
     * 서명은 그대로 검증하고, 만료(exp)만 허용합니다.
     */
    public Long getAuthIdFromExpiredAccessToken(String token) {
        try {
            return extractAuthId(parse(token));
        } catch (ExpiredJwtException e) {
            // jjwt는 서명 검증을 통과한 뒤에 만료를 검사하므로 e.getClaims()는 신뢰할 수 있음
            return extractAuthId(e.getClaims());
        }
    }

    private Long extractAuthId(Claims claims) {
        String authId = claims.get("identifier", String.class);
        
        if (authId == null || authId.trim().isEmpty()) {
            throw new IllegalArgumentException("Access token does not contain valid auth ID");
        }
        
        try {
            return Long.valueOf(authId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid auth ID format in access token");
        }
    }

    /**
     * Refresh Token에서 membername을 추출합니다.
     * @param token Refresh Token
     * @return 사용자 membername
     */
    public String getMembernameFromRefreshToken(String token) {
        Claims claims = parse(token);
        String membername = claims.get("identifier", String.class);
        
        if (membername == null || membername.trim().isEmpty()) {
            throw new IllegalArgumentException("Refresh token does not contain valid membername");
        }
        
        return membername;
    }

    /**
     * Access Token을 생성합니다.
     * @param userId 사용자 ID
     * @return 생성된 Access Token
     */
    /**
     * Access Token을 생성합니다.
     * @param authId Auth 테이블 ID
     * @return 생성된 Access Token
     */
    public String createAccessToken(Long authId) {
        if (authId == null) {
            throw new IllegalArgumentException("Auth ID cannot be null for access token creation");
        }
        return buildWithIdentifier(authId.toString(), "auth_id", ACCESS_EXP_MS, true);
    }

    /**
     * Refresh Token을 생성합니다.
     * @param userId 사용자 ID (UUID)
     * @return 생성된 Refresh Token
     */
    public String createRefreshToken(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null for refresh token creation");
        }
        return buildWithIdentifier(userId.toString(), "user_id", REFRESH_EXP_MS, false);
    }

    /**
     * 사용자 식별자를 포함한 토큰을 생성합니다.
     * @param identifier 사용자 식별자 (email 또는 membername)
     * @param identifierType 식별자 타입 ("email" 또는 "membername")
     * @param expMs 만료 시간 (밀리초)
     * @param isAccessToken Access Token인지 여부
     * @return 생성된 토큰
     */
    private String buildWithIdentifier(String identifier, String identifierType, long expMs, boolean isAccessToken) {
        return Jwts.builder()
                .setSubject(isAccessToken ? "access_token" : "refresh_token")
                .claim("identifier", identifier)
                .claim("identifierType", identifierType)
                .setId(UUID.randomUUID().toString())
                .setExpiration(Date.from(Instant.now().plusMillis(expMs)))
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 토큰이 유효한지만 확인할 때 사용합니다.
     * - 반환값은 없고, 내부적으로 parse()를 호출하여 예외 발생 여부로 유효성 판단합니다.
     * - JwtAuthFilter 등에서 인증 여부 확인 시 사용됩니다.
     *
     * @param token 클라이언트가 보낸 JWT
     * @throws io.jsonwebtoken.JwtException 유효하지 않으면 예외 발생
     */
    public void validate(String token) {
        parse(token); // ⚠️ parse 중 예외 발생 시 유효하지 않음
    }

    public long getRefreshTokenExpirationTime() {
        return REFRESH_EXP_MS;
    }

    public String validateAndGetSubject(String token) {
        Claims claims = parse(token);          // 기존 parse 로직 재사용
        return claims.getSubject();            // 유효하면 subject 반환
    }

    /**
     * Refresh Token에서 사용자 ID를 추출합니다.
     * @param token Refresh Token
     * @return 사용자 ID
     */
    public Long getUserIdFromRefreshToken(String token) {
        Claims claims = parse(token);
        String userId = claims.get("identifier", String.class);
        
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("Refresh token does not contain valid user ID");
        }
        
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid user ID format in refresh token");
        }
    }
}