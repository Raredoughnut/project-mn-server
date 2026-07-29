package com.raredonut.mnarchive.query;

import com.raredonut.mnarchive.config.MnUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Rankings", description = "플레이어 랭킹")
@RestController
@RequestMapping("/api/rankings")
public class RankingController {

    private static final int MAX_LIMIT = 200;

    private final RankingQueryRepository repo;

    public RankingController(RankingQueryRepository repo) {
        this.repo = repo;
    }

    @Operation(
            summary = "플레이어 랭킹 조회 (팝클래스 순)",
            description = """
            팝클래스 내림차순 전체 플레이어 순위.

            ```js
            fetch(`${API}/api/rankings/players?page=0&size=50`, {
              credentials: 'include',
              headers: { 'X-Requested-With': 'XMLHttpRequest' },
            })
            ```

            **랭킹 노출은 동의한 사용자만** 이다(`PUT /api/me/ranking-visibility`).
            기본값이 꺼짐이라 초기에는 목록이 비어 있는 게 정상이다.

            동의했더라도 아직 스코어를 한 번도 임포트하지 않았다면 나타나지 않는다.
            순위를 매길 팝클래스가 없기 때문이다. 프론트에서 "공개로 바꿨는데 내가 안 보인다"
            는 문의가 나올 수 있는 지점이므로, 임포트 안내와 함께 다루는 편이 좋다.

            동점 처리는 공유 순위다 — 팝클래스가 같으면 같은 순위를 받고 다음 순위가 건너뛴다
            (170.59 가 둘이면 1위, 1위, 3위).
            """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "미로그인", content = @Content)
    })
    @GetMapping("/players")
    public PlayerRankingResponse players(
            @AuthenticationPrincipal MnUser user,

            @Parameter(description = "0부터 시작하는 페이지 번호", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "페이지 크기. 최대 200으로 제한된다", example = "50")
            @RequestParam(defaultValue = "50") int size
    ) {
        int limit = Math.min(Math.max(size, 1), MAX_LIMIT);
        int offset = Math.max(page, 0) * limit;

        return new PlayerRankingResponse(
                repo.findPage(user.userId(), limit, offset),
                page,
                limit,
                repo.countVisible(),
                repo.findMyRank(user.userId()).orElse(null)
        );
    }

    @Schema(description = "랭킹 페이지 응답")
    public record PlayerRankingResponse(
            @Schema(description = "이 페이지의 랭킹 목록")
            List<PlayerRankingView> entries,

            @Schema(description = "현재 페이지 번호", example = "0")
            int page,

            @Schema(description = "실제 적용된 페이지 크기 (요청값이 200을 넘으면 200으로 잘림)",
                    example = "50")
            int size,

            @Schema(description = "랭킹에 나타나는 전체 인원 수. 공개에 동의하지 않은 사용자는 세지 않는다",
                    example = "42")
            long total,

            @Schema(description = "내 순위. 공개에 동의하지 않았거나 아직 임포트하지 않았으면 null",
                    example = "7", nullable = true)
            Long myRank
    ) {}
}
