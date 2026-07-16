package com.raredonut.mnarchive.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "charts")
public class Chart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "song_id", nullable = false)
    private Song song;

    // PostgreSQL enum(chart_difficulty) ↔ Java enum
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private Difficulty difficulty;

    // ↓ mu_top 에 없다. 시딩 전까지 null.
    private Short level;
    private Integer notes;

    protected Chart() {}

    public Long getId()               { return id; }
    public Song getSong()             { return song; }
    public Difficulty getDifficulty() { return difficulty; }
    public Short getLevel()           { return level; }
    public Integer getNotes()         { return notes; }
}
