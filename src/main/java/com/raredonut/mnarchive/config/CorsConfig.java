package com.raredonut.mnarchive.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS 설정. 두 종류의 클라이언트가 있고 성격이 정반대다.
 *
 *  1) 북마클릿  : 이게이트 오리진에서 실행. 토큰 인증, 쿠키 없음.
 *                text/plain + 표준 헤더라 프리플라이트가 없다. credentials 는 끈다.
 *  2) 프론트엔드: Next.js 에서 호출. 세션 쿠키 인증이라 credentials 가 반드시 필요하다.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /**
     * 프론트엔드 오리진 목록. 기본값은 {@code app.frontend-url} 하나이며,
     * 프리뷰 배포처럼 여러 개가 필요할 때만 CORS_ALLOWED_ORIGINS 로 덮어쓴다.
     * 오리진을 코드에 박아두면 환경변수로 뺀 frontend-url 과 어긋나므로 설정으로 뺀다.
     */
    private final String[] allowedOrigins;

    public CorsConfig(@Value("${app.allowed-origins}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 1) 북마클릿 → 백엔드. 토큰 인증, 쿠키 없음.
        //    Spring 은 등록된 순서대로 훑다가 처음 매칭되는 설정을 쓴다(더 구체적인 쪽이 아니다).
        //    그러니 이 매핑은 반드시 아래 /api/** 보다 먼저 등록되어야 한다.
        //    오리진은 이게이트 고정값이라 설정으로 뺄 이유가 없다.
        registry.addMapping("/api/imports")
                .allowedOrigins("https://p.eagate.573.jp")
                .allowedMethods("POST")
                .allowedHeaders("Content-Type")
                .allowCredentials(false)
                .maxAge(3600);

        // 2) 프론트엔드 → 백엔드. 세션 쿠키를 주고받아야 하므로 credentials(true).
        //    이때 allowedOrigins 에 "*" 는 브라우저가 거부한다 — 구체적 오리진만 나열.
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
