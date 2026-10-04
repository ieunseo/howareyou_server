package org.example.howareyou.domain.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.howareyou.domain.auth.dto.TokenBundle;
import org.example.howareyou.domain.auth.entity.Auth;
import org.example.howareyou.domain.auth.entity.Provider;
import org.example.howareyou.domain.auth.repository.AuthRepository;
import org.example.howareyou.domain.member.entity.Member;
import org.example.howareyou.domain.member.redis.MemberCacheService;
import org.example.howareyou.global.exception.CustomException;
import org.example.howareyou.global.exception.ErrorCode;
import org.example.howareyou.global.security.jwt.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * 인증 관련 서비스
 * - 로그인/로그아웃 처리
 * - 토큰 재발급
 * - 인증 정보 관리
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final AuthRepository authRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final MemberCacheService memberCacheService;
    private final PasswordEncoder passwordEncoder;

    /**
     * 이메일/비밀번호 로그인 처리
     */
    @Transactional
    public TokenBundle login(String email, String password, HttpServletRequest request) {
        // 1. 사용자 인증
        Auth auth = authRepository.findByEmailAndProvider(email, Provider.LOCAL)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_BAD_CREDENTIAL));
        
        // 2. 비밀번호 검증
        if (!passwordEncoder.matches(password, auth.getPasswordHash())) {
            throw new CustomException(ErrorCode.AUTH_BAD_CREDENTIAL);
        }

        // 3. 토큰 생성 및 사용자 캐싱
        // Access Token은 Auth ID 기반, Refresh Token은 Member ID 기반
        String accessToken = jwtTokenProvider.createAccessToken(auth.getId());
        String refreshToken = jwtTokenProvider.createRefreshToken(auth.getMember().getId());
        
        // 4. 리프레시 토큰 저장
        Instant refreshTokenExpiry = Instant.now()
                .plus(Duration.ofMillis(jwtTokenProvider.getRefreshTokenExpirationTime()));
        auth.setRefreshToken(refreshToken, refreshTokenExpiry);
        
        // 5. 사용자 정보 캐싱 (온라인 상태 관리)
        cacheUserInfo(auth);
        
        log.info("User logged in: {}", email);
        return new TokenBundle(accessToken, refreshToken, auth.getMember().isProfileCompleted());
    }
    
    /**
     * 소셜 로그인 처리
     */
    @Transactional
    public TokenBundle socialLogin(Provider provider, String email, String providerUserId, HttpServletRequest request) {
        // 1. 소셜 계정으로 인증 정보 조회
        Auth auth = authRepository.findByProviderAndProviderUserId(provider, providerUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_SOCIAL_ACCOUNT_NOT_FOUND));
        
        // 2. 토큰 생성
        // Access Token은 Auth ID 기반, Refresh Token은 Member ID 기반
        String accessToken = jwtTokenProvider.createAccessToken(auth.getId());
        String refreshToken = jwtTokenProvider.createRefreshToken(auth.getMember().getId());
        
        // 3. 리프레시 토큰 갱신
        Instant refreshTokenExpiry = Instant.now()
                .plus(Duration.ofMillis(jwtTokenProvider.getRefreshTokenExpirationTime()));
        auth.setRefreshToken(refreshToken, refreshTokenExpiry);
        
        // 4. 사용자 정보 캐싱 (온라인 상태 관리)
        cacheUserInfo(auth);
        
        log.info("Social login successful: {} - {}", provider, email);
        return new TokenBundle(accessToken, refreshToken, auth.getMember().isProfileCompleted());
    }
    
    /**
     * 로그아웃 처리
     */
    @Transactional
    public void logout(Long memberId) {
        // 1. 인증 정보에서 리프레시 토큰 제거
        authRepository.findByMemberId(memberId).ifPresent(auth -> {
            auth.invalidateRefreshToken();
            // 2. Redis에서 사용자 캐시 제거 (오프라인 상태로 변경)
            memberCacheService.delete(memberId);
            log.info("User logged out: {}", memberId);
        });
    }
    
    /**
     * 액세스 토큰 재발급
     */
    @Transactional
    public TokenBundle refreshToken(String refreshToken, String expiredAccessToken) {
        // 1. 리프레시 토큰으로 인증 정보 조회
        Auth auth = authRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_INVALID_REFRESH_TOKEN));
        
        // 2. 리프레시 토큰 유효성 검사
        if (!auth.isRefreshTokenValid(refreshToken)) {
            throw new CustomException(ErrorCode.AUTH_EXPIRED_REFRESH_TOKEN);
        }
        
        // 3. 만료된 Access Token에서 Auth ID 추출 및 검증
        try {
            // 만료된 토큰이어도 서명이 맞으면 Auth ID를 꺼냄 (기존 메서드는 만료 시 예외라 갱신이 항상 실패했음)
            Long tokenAuthId = jwtTokenProvider.getAuthIdFromExpiredAccessToken(expiredAccessToken);
            if (!tokenAuthId.equals(auth.getId())) {
                log.warn("Access token auth ID mismatch. Token: {}, DB: {}", 
                        tokenAuthId, auth.getId());
                throw new CustomException(ErrorCode.AUTH_INVALID_REFRESH_TOKEN);
            }
        } catch (Exception e) {
            log.warn("Failed to extract auth ID from expired access token: {}", e.getMessage());
            throw new CustomException(ErrorCode.AUTH_INVALID_REFRESH_TOKEN);
        }
        
        // 4. 새로운 액세스 토큰 발급 (Auth ID 기반)
        String newAccessToken = jwtTokenProvider.createAccessToken(auth.getId());
        log.info("Access token refreshed for auth ID: {}", auth.getId());
        
        return new TokenBundle(newAccessToken, refreshToken, auth.getMember().isProfileCompleted());
    }
    
    /**
     * 사용자 정보를 Redis에 캐싱 (온라인 상태 관리)
     */
    private void cacheUserInfo(Auth auth) {
        Member member = auth.getMember();
        memberCacheService.cache(member);
    }



    /**
     * Access Token 갱신 (기존 방식 - 하위 호환성)
     * @param refreshToken 클라이언트가 보낸 Refresh Token
     * @return 새로운 Access Token
     */
    @Transactional
    public String refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new CustomException(ErrorCode.AUTH_INVALID_REFRESH_TOKEN);
        }

        // 1. 토큰으로 Auth 찾기
        Auth auth = authRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_INVALID_REFRESH_TOKEN));

        // 2. 만료 확인
        if (!auth.isRefreshTokenValid(refreshToken)) {
            auth.invalidateRefreshToken();
            throw new CustomException(ErrorCode.AUTH_EXPIRED_REFRESH_TOKEN);
        }

        // 3. 새 AccessToken 발급
        Long userId = auth.getMember().getId();
        return jwtTokenProvider.createAccessToken(userId);
    }

    /**
     * 로그아웃 처리
     * @param refreshToken 클라이언트의 Refresh Token
     */
    /**
     * Refresh Token을 사용한 로그아웃 처리
     * @param refreshToken 클라이언트의 Refresh Token
     */
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            log.warn("Logout attempt with empty refresh token");
            return;
        }

        try {
            // 1. Refresh Token으로 직접 Auth 조회 (더 안전한 방법)
            authRepository.findByRefreshToken(refreshToken).ifPresent(auth -> {
                // 2. Refresh Token 무효화
                auth.invalidateRefreshToken();
                authRepository.save(auth);
                
                // 3. 사용자 캐시에서 제거 (오프라인 상태로 변경)
                Member member = auth.getMember();
                if (member != null) {
                    memberCacheService.delete(member.getId());
                    log.info("User logged out successfully: {}", member.getId());
                }
            });
        } catch (Exception e) {
            // 토큰 파싱 실패 시 로깅만 하고 종료 (이미 무효화된 토큰)
            log.warn("Error during logout: {}", e.getMessage());
        }
    }
}
