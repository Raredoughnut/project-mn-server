package com.raredonut.mnarchive.imports;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.raredonut.mnarchive.domain.PopKunType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 북마클릿이 보내오는 프로필 스냅샷. 곡 목록이 아니라 플레이 데이터 메인 페이지에서 긁는다.
 *
 * <p>모든 필드가 nullable 이다. 페이지 구조가 바뀌어 일부만 읽히는 상황이 정상 동작이어야 하고,
 * 그때 읽힌 것만 반영하고 나머지는 기존 값을 지키기 때문이다({@code COALESCE}).
 *
 * <p>{@link ScoreRow} 와 마찬가지로 사용자 브라우저에서 만들어진 값이라 전부 조작 가능한 입력이다.
 * 범위 검증은 DB 의 CHECK 제약과 여기 두 곳에서 한다 — DB 만 믿으면 위반 시 배치 전체가
 * 롤백되어 스코어까지 잃는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProfileRow(
        @Size(max = 100) String playerName,
        @Size(max = 100) String characterName,

        /** '2990-6013-2577' 형태의 친구 코드. */
        @Size(max = 40) String poptomoId,

        @DecimalMin("0") @DecimalMax("200") BigDecimal popClass,
        @Min(0) @Max(5) Short superExtraRank,

        @Min(0) Integer playCountNormal,
        @Min(0) Integer playCountExtra,
        @Min(0) Integer playCountTime10min,
        @Min(0) Integer playCountTime16min,

        // 環境設定項目. 게임 내 설정값이라 추이를 볼 대상은 아니고 현재값만 둔다.
        @Min(0) @Max(100) Short brightness,
        @Min(-50) @Max(300) Short keyBeam,
        Boolean guideLine,
        PopKunType popKun,

        /**
         * 마지막 플레이 일시. 이게이트는 '26/06/27 14時頃' 처럼 JST 시각 단위로만 보여준다.
         * 북마클릿이 +09:00 을 명시해 ISO 로 바꿔 보낸다 — 브라우저 시간대에 좌우되면 안 된다.
         * 분·초는 의미가 없다.
         */
        Instant lastPlayedAt
) {

    /** 하나도 못 읽었으면 저장할 게 없다. 파싱이 통째로 실패한 경우를 걸러낸다. */
    public boolean isEmpty() {
        return playerName == null && characterName == null && poptomoId == null
                && popClass == null && superExtraRank == null
                && playCountNormal == null && playCountExtra == null
                && playCountTime10min == null && playCountTime16min == null
                && brightness == null && keyBeam == null && guideLine == null
                && popKun == null && lastPlayedAt == null;
    }

    /** 추이 그래프에 남길 값이 있는가. 설정값(밝기 등)만 읽힌 경우는 스냅샷을 만들지 않는다. */
    public boolean hasSnapshotValues() {
        return popClass != null || superExtraRank != null
                || playCountNormal != null || playCountExtra != null
                || playCountTime10min != null || playCountTime16min != null
                || lastPlayedAt != null;
    }
}
