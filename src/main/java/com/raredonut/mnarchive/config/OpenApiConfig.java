package com.raredonut.mnarchive.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * API 문서 전반 정보.
 * 프론트엔드 팀원이 /swagger-ui.html 에서 가장 먼저 읽게 되는 내용이므로,
 * Swagger UI 만으로는 알 수 없는 인증 흐름을 여기에 적어 둔다.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI mnArchiveOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("mnArchive API")
                        .version("v0")
                        .description("""
                            Pop'n Music 스코어 트래커 백엔드.

                            ## 인증 — 세션 쿠키 방식

                            JWT 가 아니라 **세션 쿠키**를 쓴다. 따라서 모든 API 호출에
                            `credentials: 'include'` 가 필요하다. 빠뜨리면 쿠키가 실리지 않아 401 이 난다.

                            ```js
                            const API = process.env.NEXT_PUBLIC_API_BASE;  // http://localhost:8080

                            // 1) 로그인 상태 확인
                            const res = await fetch(`${API}/api/me`, {
                              credentials: 'include',
                              headers: { 'X-Requested-With': 'XMLHttpRequest' },
                            });
                            if (res.ok) { /* 로그인됨 */ } else { /* 로그인 버튼 표시 */ }

                            // 2) 로그인 시작 — fetch 가 아니라 페이지 이동!
                            window.location.href = `${API}/oauth2/authorization/google`;

                            // 3) 로그아웃
                            window.location.href = `${API}/logout`;
                            ```

                            **2번이 fetch 가 아닌 이유**: OAuth 는 구글 도메인으로 리다이렉트되는
                            방식이라 AJAX 로 처리할 수 없다. 로그인이 끝나면 백엔드가
                            프론트 주소(`app.frontend-url`)로 되돌려 보낸다.

                            **`X-Requested-With` 헤더**: 미인증 응답이 `Accept` 헤더에 따라 갈린다.
                            fetch 기본값(`Accept: */*`)이면 이 헤더가 없어도 401 이지만, `Accept` 에
                            `text/html` 이 섞이면 구글 로그인 페이지로 리다이렉트되어 fetch 가 HTML 을
                            받고 CORS 오류처럼 보인다. 이 헤더는 그 협상과 무관하게 401 을 보장한다.

                            ## 데이터 모델 요점

                            - 점수는 **append-only 이력**이다. 점수 또는 메달 등급이 나아졌을 때만
                              새 기록이 쌓이고, 조회 시에는 항상 최신 값이 나온다.
                            - `level`, `notes`, `bpm`, `version`, `duration` 은 이게이트에서 제공되지
                              않아 별도 시딩 대상이다. **현재는 대부분 null 이다.**
                            - 사용자 프로필(`popClass`, 플레이 횟수 등)은 임포트 전까지 null 이다.

                            ## 수집 경로

                            `POST /api/imports` 는 프론트가 아니라 **북마클릿**이 이게이트 페이지에서
                            직접 호출한다. 프론트가 부를 일은 없다.
                            """))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("로컬 개발"),
                        new Server().url("https://api.raredonut.com").description("운영")
                ));
    }
}
