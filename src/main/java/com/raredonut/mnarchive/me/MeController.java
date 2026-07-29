package com.raredonut.mnarchive.me;

import com.raredonut.mnarchive.config.MnUser;
import com.raredonut.mnarchive.domain.User;
import com.raredonut.mnarchive.domain.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;

@Tag(name = "Me", description = "로그인한 사용자 정보")
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final UserRepository users;

    public MeController(UserRepository users) {
        this.users = users;
    }

    @Operation(
            summary = "현재 로그인한 사용자 조회",
            description = """
            프론트엔드가 페이지 로드 시 호출해 로그인 여부를 판단하는 엔드포인트다.

            호출 방법:
            ```js
            fetch(`${API}/api/me`, {
              credentials: 'include',                            // 세션 쿠키 전송에 필수
              headers: { 'X-Requested-With': 'XMLHttpRequest' },  // 401을 받기 위해 필수
            })
            ```

            `X-Requested-With` 헤더가 없으면 미인증 시 401 대신 구글 로그인 페이지로
            리다이렉트되어, fetch 가 HTML 을 받아 CORS 오류처럼 보인다.

            로그인 시작은 이 API 가 아니다. OAuth 는 리다이렉트 방식이라 fetch 로 불가능하며,
            `window.location.href = API + '/oauth2/authorization/google'` 로 페이지를 이동시켜야 한다.
            로그인이 끝나면 백엔드가 프론트 주소로 되돌려 보낸다.
            """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 상태. 사용자 정보 반환"),
            @ApiResponse(responseCode = "401", description = "미로그인. 프론트는 로그인 버튼을 표시한다",
                    content = @Content)
    })
    @GetMapping
    public MeResponse me(@AuthenticationPrincipal MnUser principal) {
        // permitAll 로 열려 있으므로 미인증 요청에서 principal 이 null 로 들어온다.
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        User user = users.findById(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getPlayerName(),
                user.getPopClass(),
                user.getLastPlayedAt()
        );
    }

    @Schema(description = "로그인한 사용자 정보")
    public record MeResponse(
            @Schema(description = "내부 사용자 ID", example = "1")
            Long userId,

            @Schema(description = "구글 계정 이메일", example = "user@example.com")
            String email,

            @Schema(description = "이게이트 플레이어명. null 이면 아직 스코어를 한 번도 임포트하지 "
                    + "않은 사용자다 — 프론트는 이걸로 '북마클릿 설치 안내' 화면을 "
                    + "띄울지 판단할 수 있다.",
                    example = "CONST", nullable = true)
            String playerName,

            @Schema(description = "팝픈 클래스 (0~200, 소수 2자리). 임포트 전에는 null",
                    example = "170.59", nullable = true)
            BigDecimal popClass,

            @Schema(description = "마지막 플레이 일시(JST 기준, 시각 단위). 임포트 전에는 null",
                    nullable = true)
            Instant lastPlayedAt
    ) {}
}
