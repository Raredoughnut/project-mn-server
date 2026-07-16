package com.raredonut.mnarchive.domain;

import jakarta.persistence.*;

/**
 * 룩업 테이블. code 가 자연키.
 * rank_order 가 있어야 '점수는 그대로인데 메달이 올랐다'를 감지할 수 있다.
 */
@Entity
@Table(name = "clear_medals")
public class ClearMedal {

    @Id
    private String code;

    @Column(nullable = false)
    private String label;

    /** 클수록 상위. 미상 메달이면 null. */
    @Column(name = "rank_order")
    private Short rankOrder;

    /** 파서가 보는 이미지 파일명 (meda_a ~ meda_l, meda_none) */
    @Column(name = "eagate_code")
    private String eagateCode;

    protected ClearMedal() {}

    public String getCode()       { return code; }
    public String getLabel()      { return label; }
    public Short getRankOrder()   { return rankOrder; }
    public String getEagateCode() { return eagateCode; }
}
