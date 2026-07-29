package com.raredonut.mnarchive.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.RequestHeaderRequestMatcher;

@Configuration
public class SecurityConfig {

    /** 문서가 꺼진 환경에서는 springdoc 이 엔드포인트를 만들지 않으므로 열어 둘 이유도 없다. */
    private static final String[] API_DOCS_PATHS =
            {"/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"};

    private final MnOidcUserService oidcUserService;
    private final String frontendUrl;
    private final boolean apiDocsEnabled;

    public SecurityConfig(MnOidcUserService oidcUserService,
                          @Value("${app.frontend-url}") String frontendUrl,
                          @Value("${app.api-docs-enabled}") boolean apiDocsEnabled) {
        this.oidcUserService = oidcUserService;
        this.frontendUrl = frontendUrl;
        this.apiDocsEnabled = apiDocsEnabled;
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
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/imports"))

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
