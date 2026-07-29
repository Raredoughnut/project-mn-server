package com.raredonut.mnarchive.config;

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

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 1) 북마클릿 → 백엔드. 토큰 인증, 쿠키 없음. 더 구체적인 매핑이라 /api/** 보다 우선한다.
        registry.addMapping("/api/imports")
                .allowedOrigins("https://p.eagate.573.jp")
                .allowedMethods("POST")
                .allowedHeaders("Content-Type")
                .allowCredentials(false)
                .maxAge(3600);

        // 2) 프론트엔드 → 백엔드. 세션 쿠키를 주고받아야 하므로 credentials(true).
        //    이때 allowedOrigins 에 "*" 는 브라우저가 거부한다 — 구체적 오리진만 나열.
        registry.addMapping("/api/**")
                .allowedOrigins(
                        "http://localhost:3000",        // 로컬 개발
                        "https://mn.raredonut.com"      // 운영 프론트
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
