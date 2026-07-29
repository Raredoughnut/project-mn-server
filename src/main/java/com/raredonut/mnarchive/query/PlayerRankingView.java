package com.raredonut.mnarchive.query;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 랭킹 한 줄. **남에게 보여도 되는 필드만** 담는다.
 *
 * users 에는 email(UNIQUE)과 poptomo_id(친구 코드) 가 함께 있다. 편하다고
 * 엔티티나 MeResponse 를 그대로 흘리면 그 두 개가 새어 나간다. 그래서 공개용은
 * 이 record 로만 나가고, 필드를 늘릴 때는 "이걸 모르는 사람이 봐도 되는가" 를
 * 먼저 통과시킨다.
 */
@Schema(description = "랭킹 한 줄. 공개 동의한 사용자만 나타난다")
public record PlayerRankingView(
        @Schema(description = "순위. 팝클래스가 같으면 같은 순위를 공유하고 그 다음이 건너뛴다 "
                + "(170.59 가 둘이면 1, 1, 3)",
                example = "1")
        long rank,

        @Schema(description = "이게이트 플레이어명", example = "CONST")
        String playerName,

        @Schema(description = "사용 캐릭터명", example = "パラボー", nullable = true)
        String characterName,

        @Schema(description = "팝픈 클래스 (0~200, 소수 2자리)", example = "170.59")
        BigDecimal popClass,

        @Schema(description = "이 줄이 나 자신인지. 프론트에서 강조 표시에 쓴다", example = "false")
        boolean me
) {}
