package com.raredonut.mnarchive.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/**
 * APPEND-ONLY.
 *
 * setter 가 없고 @Immutable 이 붙은 것은 의도적이다. 이력 테이블의 과거 행이 조용히
 * 수정되면 '이전과 비교'라는 이 서비스의 존재 이유가 무너진다. Hibernate 는 @Immutable
 * 엔티티에 대해 UPDATE 를 아예 발행하지 않는다.
 *
 * 새 기록은 새 행으로만 추가된다(그 삽입은 ImportService 가 JdbcClient 로 배치 처리).
 * 현재 기록은 UserChartCurrent(뷰)로 읽는다.
 */
@Entity
@Immutable
@Table(name = "score_records")
public class ScoreRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "chart_id", nullable = false)
    private Long chartId;

    @Column(name = "import_batch_id", nullable = false)
    private Long importBatchId;

    @Column(nullable = false)
    private Integer score;

    /** clear_medals.code. 미상 메달이 있을 수 있으므로 nullable. */
    @Column(name = "medal_code")
    private String medalCode;

    @Column(name = "recorded_at", insertable = false, updatable = false)
    private Instant recordedAt;

    protected ScoreRecord() {}

    public Long getId()            { return id; }
    public Long getUserId()        { return userId; }
    public Long getChartId()       { return chartId; }
    public Long getImportBatchId() { return importBatchId; }
    public Integer getScore()      { return score; }
    public String getMedalCode()   { return medalCode; }
    public Instant getRecordedAt() { return recordedAt; }
}
