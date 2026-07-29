package com.raredonut.mnarchive.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 신원 + 이게이트 프로필의 '현재값' 캐시.
 *
 * pop_class, play_count_* 는 시간에 따라 변한다. 여기 있는 값은 조회 편의를 위한 캐시일 뿐
 * 진실의 원천이 아니다 — 이력은 user_profile_snapshots 가 보관하고, 팝클래스 추이 그래프는
 * 거기서 나온다.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Google ID 토큰의 sub. 이메일이 아니다 — 이메일은 바뀔 수 있다. */
    @Column(name = "google_id", nullable = false, unique = true, updatable = false)
    private String googleId;

    @Column(unique = true)
    private String email;

    /** 북마클릿 토큰의 SHA-256 hex. 평문은 저장하지 않는다. */
    @Column(name = "api_token", unique = true)
    private String apiToken;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false)
    private Instant updatedAt;

    // ── 이게이트에서 긁어온 값 (최신 스냅샷) ──────────────────────────────
    @Column(name = "player_name")    private String playerName;
    @Column(name = "poptomo_id")     private String poptomoId;
    @Column(name = "character_name") private String characterName;
    @Column(name = "pop_class")      private BigDecimal popClass;   // numeric(5,2). double 금지
    @Column(name = "super_extra_rank") private Short superExtraRank;

    @Column(name = "play_count_normal")     private Integer playCountNormal;
    @Column(name = "play_count_extra")      private Integer playCountExtra;
    @Column(name = "play_count_time_10min") private Integer playCountTime10min;
    @Column(name = "play_count_time_16min") private Integer playCountTime16min;

    @Column(name = "brightness") private Short brightness;
    @Column(name = "key_beam")   private Short keyBeam;
    @Column(name = "guide_line") private Boolean guideLine;


    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "pop_kun")
    private PopKunType popKun;


    /** '26/06/27 14時頃' — JST, 시각 단위. 분·초는 신뢰하지 말 것. */
    @Column(name = "last_played_at") private Instant lastPlayedAt;

    /**
     * 랭킹 노출 동의 여부. 기본 false — 사용자가 직접 켜야 공개 목록에 나타난다.
     * 서비스 자체는 전체 공개지만 자기 기록을 남에게 보일지는 별개 선택이다.
     */
    @Column(name = "ranking_visible", nullable = false)
    private boolean rankingVisible;

    protected User() {}

    public static User ofGoogle(String googleId, String email) {
        User u = new User();
        u.googleId = googleId;
        u.email = email;
        return u;
    }

    /** 로그인할 때마다 이메일이 바뀌었을 수 있다(구글 계정 변경). googleId 는 절대 바뀌지 않는다. */
    public void syncEmail(String email) {
        this.email = email;
    }

    /** 랭킹 노출을 켜고 끈다. 끄면 다음 조회부터 공개 목록에서 사라진다. */
    public void changeRankingVisibility(boolean visible) {
        this.rankingVisible = visible;
    }

    public Long getId()         { return id; }
    public String getGoogleId() { return googleId; }
    public String getEmail()    { return email; }
    public String getPlayerName() { return playerName; }
    public BigDecimal getPopClass() { return popClass; }
    public Instant getLastPlayedAt() { return lastPlayedAt; }
    public boolean isRankingVisible() { return rankingVisible; }
}
