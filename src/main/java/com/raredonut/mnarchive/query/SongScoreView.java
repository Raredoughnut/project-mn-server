package com.raredonut.mnarchive.query;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 곡 목록 화면의 한 행. 곡 1개 + 난이도별 현재 점수/메달을 피벗한 형태.
 *
 * score_records 는 보면 단위 행이지만 화면은 곡 단위 가로 배열이라, SQL FILTER 로 피벗한다.
 */
@Schema(description = """
        곡 1개와 난이도별 현재 기록. LIGHT/NORMAL/HYPER/EX 4난이도가 가로로 펼쳐진다.

        null 의 의미가 두 가지이니 주의:
          - {difficulty}Level 이 null  → 레벨 정보가 아직 시딩되지 않음 (보면은 존재할 수 있음)
          - {difficulty}Score 가 null  → 그 난이도 보면이 없거나, 있어도 아직 플레이하지 않음
        """)
public record SongScoreView(
        @Schema(description = "내부 곡 ID", example = "42")
        long songId,

        @Schema(description = "이게이트 곡 식별자. mu_detail?no= 의 값", example = "Hk+3PjqHbOHlRzL46XN6Cw==")
        String eagateSongNo,

        @Schema(description = "곡명(曲名)", example = "Daisuke")
        String title,

        @Schema(description = "장르명(ジャンル名). 팝픈에서 곡을 대표하는 이름", example = "ユーロビート")
        String genre,

        @Schema(description = "수록 버전 번호. 시딩 전에는 null", example = "29", nullable = true)
        Short version,

        @Schema(description = "LIGHT 레벨 (1~50). 시딩 전에는 null", nullable = true)
        Short lightLevel,
        @Schema(description = "LIGHT 현재 최고 점수 (0~100000)", nullable = true)
        Integer lightScore,
        @Schema(description = "LIGHT 클리어 메달 코드. clear_medals.code 값", example = "gold_star", nullable = true)
        String lightMedal,

        @Schema(description = "NORMAL 레벨 (1~50). 시딩 전에는 null", nullable = true)
        Short normalLevel,
        @Schema(description = "NORMAL 현재 최고 점수", nullable = true)
        Integer normalScore,
        @Schema(description = "NORMAL 클리어 메달 코드", nullable = true)
        String normalMedal,

        @Schema(description = "HYPER 레벨 (1~50). 시딩 전에는 null", nullable = true)
        Short hyperLevel,
        @Schema(description = "HYPER 현재 최고 점수", nullable = true)
        Integer hyperScore,
        @Schema(description = "HYPER 클리어 메달 코드", nullable = true)
        String hyperMedal,

        @Schema(description = "EX 레벨 (1~50). 시딩 전에는 null", nullable = true)
        Short exLevel,
        @Schema(description = "EX 현재 최고 점수", example = "99319", nullable = true)
        Integer exScore,
        @Schema(description = "EX 클리어 메달 코드", example = "gold_star", nullable = true)
        String exMedal
) {}
