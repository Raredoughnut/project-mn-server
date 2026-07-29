package com.raredonut.mnarchive.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.util.matcher.RequestHeaderRequestMatcher;
import org.springframework.util.StringUtils;

@Configuration
public class SecurityConfig {

    /** 문서가 꺼진 환경에서는 springdoc 이 엔드포인트를 만들지 않으므로 열어 둘 이유도 없다. */
    private static final String[] API_DOCS_PATHS =
            {"/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"};

    private final MnOidcUserService oidcUserService;
    private final String frontendUrl;
    private final boolean apiDocsEnabled;
    private final String cookieDomain;

    public SecurityConfig(MnOidcUserService oidcUserService,
                          @Value("${app.frontend-url}") String frontendUrl,
                          @Value("${app.api-docs-enabled}") boolean apiDocsEnabled,
                          @Value("${app.cookie-domain:}") String cookieDomain) {
        this.oidcUserService = oidcUserService;
        this.frontendUrl = frontendUrl;
        this.apiDocsEnabled = apiDocsEnabled;
        this.cookieDomain = cookieDomain;
    }

    /**
     * CSRF 토큰을 담을 쿠키.
     *
     * 프론트와 백엔드가 다른 호스트라 도메인 설정이 필요하다. 로컬은 둘 다 localhost 라
     * (포트만 다르고 쿠키는 포트를 구분하지 않는다) 기본값인 host-only 로 충분하지만,
     * 운영은 mn.raredonut.com 이 api.raredonut.com 이 심은 쿠키를 읽어야 하므로
     * 상위 도메인(raredonut.com)으로 올려야 한다. APP_COOKIE_DOMAIN 으로 지정한다.
     */
    private CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        if (StringUtils.hasText(cookieDomain)) {
            repository.setCookieCustomizer(cookie -> cookie.domain(cookieDomain));
        }
        return repository;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> {
                    // 북마클릿 업로드: 세션이 아니라 바디의 토큰으로 인증한다(ImportService 가 처리).
                    // cross-site 요청이라 세션 쿠키가 실리지 않으므로 permitAll 이어야 한다.
                    auth.requestMatchers("/api/imports").permitAll();

                    // /api/me 는 미로그인 상태에서도 '401'을 돌려줘야 한다.
                    // 인증을 강제하면 로그인 페이지로 리다이렉트되어, 프론트가 상태를 판별할 수 없다.
                    auth.requestMatchers("/api/me").permitAll();

                    auth.requestMatchers("/", "/login/**", "/oauth2/**", "/error").permitAll();

                    // API 문서는 켜져 있을 때만 연다. 끄면 springdoc 이 엔드포인트를 등록하지 않아
                    // 어차피 404 지만, 규칙까지 같이 닫아 두면 설정이 어긋나도 노출되지 않는다.
                    if (apiDocsEnabled) {
                        auth.requestMatchers(API_DOCS_PATHS).permitAll();
                    }

                    auth.anyRequest().authenticated();
                })

                // API 요청(XHR/fetch)에는 로그인 페이지로 리다이렉트하지 않고 401 을 준다.
                // 리다이렉트를 주면 프론트의 fetch 가 구글 로그인 HTML 을 받아버려 CORS 오류로 보인다.
                // Accept 가 */* 이면 기본 동작으로도 401 이 나오지만, text/html 이 섞이는 순간
                // 리다이렉트로 바뀐다. 이 규칙은 Accept 협상과 무관하게 401 을 고정한다.
                .exceptionHandling(ex -> ex
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                new RequestHeaderRequestMatcher("X-Requested-With", "XMLHttpRequest"))
                )

                // /api/imports 는 쿠키를 쓰지 않으므로 CSRF 대상이 아니다. 나머지는 CSRF 유지.
                //
                // 기본 저장소(HttpSessionCsrfTokenRepository)는 토큰을 세션에만 두는데,
                // 그러면 별도 오리진의 SPA 가 토큰을 얻을 방법이 없어 모든 상태 변경 요청이
                // 403 이 된다. HttpOnly 를 끈 쿠키에 실어 프론트가 읽어 갈 수 있게 한다.
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/api/imports")
                        .csrfTokenRepository(csrfTokenRepository())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                )

                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(ui -> ui.oidcUserService(oidcUserService))
                        // 로그인이 끝나면 백엔드가 아니라 프론트로 돌려보낸다.
                        // 사용자는 세션 쿠키를 지닌 채 프론트 화면으로 복귀한다.
                        .defaultSuccessUrl(frontendUrl, true)
                )

                .logout(logout -> logout
                        .logoutSuccessUrl(frontendUrl)
                );

        return http.build();
    }
}
