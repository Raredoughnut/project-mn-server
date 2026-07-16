package com.raredonut.mnarchive.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.io.Serializable;
import java.time.Instant;

/**
 * user_chart_current 뷰 매핑. 조회 전용.
 * (user_id, chart_id) 복합키라 @IdClass 가 필요하다.
 */
@Entity
@Immutable
@Table(name = "user_chart_current")
@IdClass(UserChartCurrent.Key.class)
public class UserChartCurrent {

    @Id @Column(name = "user_id")  private Long userId;
    @Id @Column(name = "chart_id") private Long chartId;

    private Integer score;

    @Column(name = "medal_code")  private String medalCode;
    @Column(name = "recorded_at") private Instant recordedAt;

    protected UserChartCurrent() {}

    public Long getUserId()        { return userId; }
    public Long getChartId()       { return chartId; }
    public Integer getScore()      { return score; }
    public String getMedalCode()   { return medalCode; }
    public Instant getRecordedAt() { return recordedAt; }

    public record Key(Long userId, Long chartId) implements Serializable {}
}
