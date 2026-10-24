package org.example.howareyou.domain.auth.oauth2.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.example.howareyou.domain.auth.dto.TokenBundle;
import org.example.howareyou.domain.auth.entity.Provider;
import org.example.howareyou.domain.auth.oauth2.processor.OAuth2LoginProcessor;
import org.example.howareyou.global.exception.CustomException;
import org.example.howareyou.global.exception.ErrorCode;
import org.example.howareyou.global.util.CookieUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 소셜 로그인 성공 시, 공급자별 {@link OAuth2LoginProcessor} 로 후처리하고
 * <p>Access·Refresh 토큰을 내려준 뒤 <b>프런트엔드로 리다이렉트</b>한다.</p>
 *
 * <p><b>리다이렉트 주소</b>는 <code>application.yml</code> 의
 * <code>front.url</code> 프로퍼티(@Value 주입)로 외부 설정 가능.</p>
 */
@Slf4j
@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    /** 공급자 → 후처리기 Map (생성자에서 List → Map 변환) */
    private final Map<Provider, OAuth2LoginProcessor> processorMap;

    /** front.url → 없으면 FRONT_URL env → 없으면 기본 */
    @Value("${front.url:${FRONT_URL:http://localhost:3000}}")
    private String frontUrl;
    
    /** 개발 환경 여부 */
    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    /**
     * 로그인 후 이동 경로 강제 지정 (백엔드 단독 테스트용, 예: /test-login.html)
     * 비어 있으면 기존대로 /login/success 또는 /signup/membername
     */
    @Value("${OAUTH2_REDIRECT_PATH:}")
    private String redirectPathOverride;


    public OAuth2SuccessHandler(List<OAuth2LoginProcessor> list) {
        this.processorMap = list.stream()
                .collect(Collectors.toMap(OAuth2LoginProcessor::provider, p -> p));
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest req,
                                        HttpServletResponse res,
                                        Authentication auth) throws IOException {

        /* 1) registrationId → Provider enum */
        String id = ((OAuth2AuthenticationToken) auth).getAuthorizedClientRegistrationId();
        Provider provider = Provider.valueOf(id.toUpperCase());

        /* 2) 프로세서 실행 */
        OAuth2LoginProcessor proc = processorMap.get(provider);
        if (proc == null) {
            throw new CustomException(ErrorCode.UNSUPPORTED_PROVIDER, 
                "지원하지 않는 OAuth2 공급자입니다: " + provider);
        }

        // 2) 프로세서 실행
        TokenBundle t = proc.process((OAuth2User)auth.getPrincipal(), req);

        // 3-1) 토큰 전달 - Refresh Token만 쿠키에 설정
        // 개발 환경에서는 secure=false, 프로덕션에서는 secure=true
        boolean isSecure = !"dev".equals(activeProfile);
        res.addCookie(CookieUtils.refresh(t.refresh(), isSecure));

        // 3-2) 목적지 결정 (프로필 완료 상태에 따라)
        String redirectUrl;
        String path;
        
        if (t.completed()) {
            // 프로필 완료 → 로그인 성공 페이지
            path = "/login/success";
        } else {
            // 프로필 미완료 → membername 설정 페이지
            path = "/signup/membername";
        }

        // 프론트 없이 백엔드만 테스트할 때: 설정된 경로로 강제 이동
        if (!redirectPathOverride.isBlank()) {
            path = redirectPathOverride;
        }

        redirectUrl = UriComponentsBuilder
                .fromUriString(frontUrl)
                .path(path)
                .queryParam("oauth_success", "true")
                .queryParam("provider", provider.name().toLowerCase())
                .queryParam("profile_completed", String.valueOf(t.completed()))
                .queryParam("access_token", t.access())  // Access Token 추가
                .build().toUriString();
        
        log.info("🔄 OAuth2 리다이렉트: {}", redirectUrl);

        getRedirectStrategy().sendRedirect(req, res, redirectUrl);

    }
}