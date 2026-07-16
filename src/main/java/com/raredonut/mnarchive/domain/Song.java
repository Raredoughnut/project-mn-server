package com.raredonut.mnarchive.domain;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "songs")
public class Song {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** mu_detail?no=... 의 값. 임포트 매칭 키. */
    @Column(name = "eagate_song_no", nullable = false, unique = true)
    private String eagateSongNo;

    // ↓ 임포트가 채운다
    @Column(nullable = false) private String title;
    private String genre;
    private String artist;

    // ↓ 직접 시딩할 값. 임포트는 절대 건드리지 않는다.
    private Short version;
    @Column(name = "duration_seconds") private Integer durationSeconds;
    @Column(name = "min_bpm") private Short minBpm;
    @Column(name = "avg_bpm") private Short avgBpm;
    @Column(name = "max_bpm") private Short maxBpm;

    @OneToMany(mappedBy = "song")
    private List<Chart> charts = new ArrayList<>();

    protected Song() {}

    public Long getId()             { return id; }
    public String getEagateSongNo() { return eagateSongNo; }
    public String getTitle()        { return title; }
    public String getGenre()        { return genre; }
    public String getArtist()       { return artist; }
    public Short getVersion()       { return version; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public Short getMinBpm()        { return minBpm; }
    public Short getAvgBpm()        { return avgBpm; }
    public Short getMaxBpm()        { return maxBpm; }
    public List<Chart> getCharts()  { return charts; }
}
