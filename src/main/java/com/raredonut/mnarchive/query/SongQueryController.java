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
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Songs", description = "곡 목록과 점수 조회")
@RestController
@RequestMapping("/api/songs")
public class SongQueryController {

    private static final int MAX_LIMIT = 200;

    private final SongQueryRepository repo;

    public SongQueryController(SongQueryRepository repo) {
        this.repo = repo;
    }

    @Operation(
            summary = "곡 목록 + 내 점수 조회",
            description = """
            로그인한 사용자의 난이도별 현재 최고 기록을 곡 단위로 묶어 반환한다.

            호출 방법:
            ```js
            fetch(`${API}/api/songs?page=0&size=50`, { credentials: 'include' })
            ```

            아직 플레이하지 않은 보면도 포함된다. charts(보면 마스터)를 기준으로 조회하므로
            "레벨은 있는데 점수가 비어 있는" 칸이 나오며, 이걸로 '다음에 칠 곡' 화면을 만들 수 있다.

            점수는 append-only 이력의 최신 값이다. 임포트할 때 점수 또는 메달 등급이
            나아진 경우에만 새 기록이 쌓인다.
            """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "미로그인", content = @Content)
    })
    @GetMapping
    public SongListResponse list(
            @AuthenticationPrincipal MnUser user,

            @Parameter(description = "0부터 시작하는 페이지 번호", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "페이지 크기. 최대 200으로 제한된다", example = "50")
            @RequestParam(defaultValue = "50") int size
    ) {
        int limit = Math.min(Math.max(size, 1), MAX_LIMIT);
        int offset = Math.max(page, 0) * limit;

        List<SongScoreView> songs = repo.findSongScores(user.userId(), limit, offset);
        long total = repo.countSongs();

        return new SongListResponse(songs, page, limit, total);
    }

    @Schema(description = "곡 목록 페이지 응답")
    public record SongListResponse(
            @Schema(description = "이 페이지의 곡 목록")
            List<SongScoreView> songs,

            @Schema(description = "현재 페이지 번호", example = "0")
            int page,

            @Schema(description = "실제 적용된 페이지 크기 (요청값이 200을 넘으면 200으로 잘림)", example = "50")
            int size,

            @Schema(description = "전체 곡 수", example = "940")
            long total
    ) {}
}
