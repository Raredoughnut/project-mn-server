package com.raredonut.mnarchive.query;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 곡 목록 조회. JPA 로는 피벗이 어색해서 네이티브 쿼리를 쓴다.
 *
 * charts 를 기준으로 삼는 이유: 사용자가 아직 안 친 보면도 목록에 보여야 하기 때문이다
 * (레벨은 있는데 점수가 없는 칸). score 는 user_chart_current 를 LEFT JOIN 해서 채운다.
 */
@Repository
public class SongQueryRepository {

    private final JdbcClient jdbc;

    public SongQueryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static final String BASE_SQL = """
        SELECT s.id            AS song_id,
               s.eagate_song_no,
               s.title,
               s.genre,
               s.version,
               MAX(c.level)  FILTER (WHERE c.difficulty = 'LIGHT')  AS light_level,
               MAX(cur.score) FILTER (WHERE c.difficulty = 'LIGHT')  AS light_score,
               MAX(cur.medal_code) FILTER (WHERE c.difficulty = 'LIGHT') AS light_medal,
               MAX(c.level)  FILTER (WHERE c.difficulty = 'NORMAL') AS normal_level,
               MAX(cur.score) FILTER (WHERE c.difficulty = 'NORMAL') AS normal_score,
               MAX(cur.medal_code) FILTER (WHERE c.difficulty = 'NORMAL') AS normal_medal,
               MAX(c.level)  FILTER (WHERE c.difficulty = 'HYPER')  AS hyper_level,
               MAX(cur.score) FILTER (WHERE c.difficulty = 'HYPER')  AS hyper_score,
               MAX(cur.medal_code) FILTER (WHERE c.difficulty = 'HYPER') AS hyper_medal,
               MAX(c.level)  FILTER (WHERE c.difficulty = 'EX')     AS ex_level,
               MAX(cur.score) FILTER (WHERE c.difficulty = 'EX')     AS ex_score,
               MAX(cur.medal_code) FILTER (WHERE c.difficulty = 'EX') AS ex_medal
        FROM   songs s
        JOIN   charts c ON c.song_id = s.id
        LEFT   JOIN user_chart_current cur
               ON cur.chart_id = c.id AND cur.user_id = :userId
        GROUP  BY s.id, s.eagate_song_no, s.title, s.genre, s.version
        ORDER  BY s.title
        LIMIT  :limit OFFSET :offset
        """;

    public List<SongScoreView> findSongScores(long userId, int limit, int offset) {
        return jdbc.sql(BASE_SQL)
                .param("userId", userId)
                .param("limit", limit)
                .param("offset", offset)
                .query(SongScoreView.class)
                .list();
    }

    public long countSongs() {
        return jdbc.sql("SELECT count(*) FROM songs")
                .query(Long.class).single();
    }
}
