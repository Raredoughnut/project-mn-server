package com.raredonut.mnarchive.imports;

import com.raredonut.mnarchive.domain.Difficulty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;

/**
 * 북마클릿이 보내오는 한 보면의 스냅샷.
 *
 * 파서가 사용자의 브라우저에서 돌고 외부 JS 로 배포되므로, 이 값들은 전부 '조작 가능한 입력'이다.
 * 파싱을 클라이언트에 둔 대가로 검증은 서버가 전적으로 책임진다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScoreRow(
        @NotBlank @Size(max = 64) String eagateSongNo,
        @NotBlank @Size(max = 300) String title,
        @Size(max = 300) String genre,
        @Size(max = 300) String artist,
        @NotNull Difficulty difficulty,
        @NotNull @Min(0) @Max(100_000) Integer score,   // 0 = 미플레이(보면은 존재)
        @Size(max = 32) String medalCode                // 'meda_a' 등 이게이트 코드
) {}
