package com.raredonut.mnarchive.imports;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.*;

/**
 * 임포트 파이프라인. 조회는 JPA 를 쓰지만 여기만 JdbcClient 다.
 *
 * 이유: 한 번에 곡 수천 개 × 보면 4개를 upsert 한다. JPA 는 save() 마다 SELECT 를 날리고
 * 영속성 컨텍스트에 만 개 엔티티를 쌓는다. 무엇보다 ON CONFLICT DO UPDATE 를 표현할 방법이 없다.
 *
 * 순서:
 *   1. 토큰 → 사용자
 *   2. 체크섬으로 중복 업로드 조기 차단 (북마클릿 재클릭 idempotent)
 *   3. songs / charts upsert  ← '-'(보면 없음)가 아닌 모든 컬럼. 보면 마스터가 자동 구축된다
 *   4. 현재 베스트와 비교 → 점수 또는 메달 등급이 나아진 것만 score_records insert
 */
@Service
public class ImportService {

    private final JdbcClient jdbc;
    private final DataSource dataSource;
    private final UserTokenService tokenService;

    public ImportService(JdbcClient jdbc, DataSource dataSource, UserTokenService tokenService) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
        this.tokenService = tokenService;
    }

    @Transactional
    public ImportResult ingest(String token, String source, List<ScoreRow> rows, ProfileRow profile) {
        long userId = tokenService.resolveUserId(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        String checksum = checksum(rows);

        // 프로필 현재값은 중복 업로드여도 반영한다. 점수가 하나도 안 올라도 플레이 횟수나
        // 마지막 플레이 시각은 달라져 있을 수 있고, 덮어써도 잃는 정보가 없다.
        if (profile != null && !profile.isEmpty()) {
            updateUserProfile(userId, profile);
        }

        // 2. 중복 업로드 --------------------------------------------------------
        Optional<Long> dup = jdbc.sql("SELECT id FROM import_batches WHERE user_id = ? AND payload_checksum = ?")
                .params(userId, checksum)
                .query(Long.class).optional();
        if (dup.isPresent()) {
            // 스냅샷은 남기지 않는다 — import_batch_id 가 NOT NULL 이라 붙일 배치가 없다.
            // 체크섬이 스코어만 보므로, 프로필만 변한 업로드는 추이에 기록되지 않는다.
            // 팝클래스는 점수가 올라야 오르므로 실질적 누락은 아니지만, 플레이 횟수 추이를
            // 정밀하게 봐야 한다면 체크섬에 프로필을 포함시키는 쪽으로 바꿔야 한다.
            return new ImportResult(dup.get(), rows.size(), 0, 0, true);
        }

        long batchId;
        try {
            batchId = jdbc.sql("""
                    INSERT INTO import_batches (user_id, source, status, payload_checksum, parsed_chart_count)
                    VALUES (?, ?::import_source, 'RECEIVED', ?, ?)
                    RETURNING id
                    """)
                    .params(userId, normalizeSource(source), checksum, rows.size())
                    .query(Long.class).single();
        } catch (DuplicateKeyException e) {
            // 동시에 두 번 클릭. 경쟁에서 진 쪽은 중복으로 처리한다.
            Long id = jdbc.sql("SELECT id FROM import_batches WHERE user_id = ? AND payload_checksum = ?")
                    .params(userId, checksum).query(Long.class).single();
            return new ImportResult(id, rows.size(), 0, 0, true);
        }

        // 3. 마스터 upsert ------------------------------------------------------
        upsertSongs(rows);
        Map<String, Long> songIds = loadSongIds(rows);
        int newCharts = upsertCharts(rows, songIds);
        Map<ChartKey, Long> chartIds = loadChartIds(songIds.values());

        // 4. 변경분만 insert ----------------------------------------------------
        Map<String, Short> medalRank = loadMedalRanks();       // eagate_code → rank_order
        Map<String, String> medalCode = loadMedalCodes();      // eagate_code → code
        Map<Long, Current> current = loadCurrentBests(userId, chartIds.values());

        List<Object[]> inserts = new ArrayList<>();
        for (ScoreRow r : rows) {
            if (r.score() == null || r.score() <= 0) continue;   // 미플레이는 스코어를 만들지 않는다

            Long songId = songIds.get(r.eagateSongNo());
            if (songId == null) continue;
            Long chartId = chartIds.get(new ChartKey(songId, r.difficulty().name()));
            if (chartId == null) continue;

            String medal = medalCode.get(r.medalCode());        // 미등록 eagate_code 면 null
            Current cur = current.get(chartId);
            if (cur != null && !improved(cur, r.score(), medal, medalRank, medalCode)) continue;

            inserts.add(new Object[]{ userId, chartId, batchId, r.score(), medal });
        }

        if (!inserts.isEmpty()) batchInsertScores(inserts);

        // 5. 프로필 이력 --------------------------------------------------------
        if (profile != null && profile.hasSnapshotValues()) {
            insertProfileSnapshot(userId, batchId, profile);
        }

        jdbc.sql("UPDATE import_batches SET status = 'PARSED', changed_chart_count = ? WHERE id = ?")
                .params(inserts.size(), batchId).update();

        return new ImportResult(batchId, rows.size(), inserts.size(), newCharts, false);
    }

    // -------------------------------------------------------------------------
    // 프로필
    //   users 는 '현재값 캐시', user_profile_snapshots 가 이력의 원천이다.
    // -------------------------------------------------------------------------

    /**
     * 읽힌 필드만 덮어쓴다.
     *
     * <p>파서는 사용자 브라우저에서 돌고 이게이트 페이지 구조는 언제든 바뀐다. 통짜로 대입하면
     * 셀렉터 하나가 어긋난 날 멀쩡하던 값이 통째로 null 이 된다. COALESCE 로 "못 읽었으면
     * 건드리지 않는다"를 DB 수준에서 보장한다.
     *
     * <p>updated_at 은 건드리지 않는다 — V3 의 touch_updated_at 트리거가 채운다.
     */
    private void updateUserProfile(long userId, ProfileRow p) {
        jdbc.sql("""
                UPDATE users SET
                    player_name           = COALESCE(?, player_name),
                    character_name        = COALESCE(?, character_name),
                    poptomo_id            = COALESCE(?, poptomo_id),
                    pop_class             = COALESCE(?, pop_class),
                    super_extra_rank      = COALESCE(?, super_extra_rank),
                    play_count_normal     = COALESCE(?, play_count_normal),
                    play_count_extra      = COALESCE(?, play_count_extra),
                    play_count_time_10min = COALESCE(?, play_count_time_10min),
                    play_count_time_16min = COALESCE(?, play_count_time_16min),
                    brightness            = COALESCE(?, brightness),
                    key_beam              = COALESCE(?, key_beam),
                    guide_line            = COALESCE(?, guide_line),
                    pop_kun               = COALESCE(CAST(? AS pop_kun_type), pop_kun),
                    last_played_at        = COALESCE(?, last_played_at)
                WHERE id = ?
                """)
                .params(p.playerName(), p.characterName(), p.poptomoId(),
                        p.popClass(), p.superExtraRank(),
                        p.playCountNormal(), p.playCountExtra(),
                        p.playCountTime10min(), p.playCountTime16min(),
                        p.brightness(), p.keyBeam(), p.guideLine(),
                        p.popKun() == null ? null : p.popKun().name(),
                        p.lastPlayedAt() == null ? null : java.sql.Timestamp.from(p.lastPlayedAt()),
                        userId)
                .update();
    }

    /** 배치 1회 = 스냅샷 1행. 팝클래스 추이 그래프가 여기서 나온다. */
    private void insertProfileSnapshot(long userId, long batchId, ProfileRow p) {
        jdbc.sql("""
                INSERT INTO user_profile_snapshots
                    (user_id, import_batch_id, pop_class, super_extra_rank,
                     play_count_normal, play_count_extra,
                     play_count_time_10min, play_count_time_16min, last_played_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)
                .params(userId, batchId, p.popClass(), p.superExtraRank(),
                        p.playCountNormal(), p.playCountExtra(),
                        p.playCountTime10min(), p.playCountTime16min(),
                        p.lastPlayedAt() == null ? null : java.sql.Timestamp.from(p.lastPlayedAt()))
                .update();
    }

    // -------------------------------------------------------------------------
    // 변경 판정
    //   점수와 메달은 팝픈에서 독립적으로 갱신된다. 점수만 비교하면 메달 승급을 놓친다.
    //   등급을 모르는 메달(meda_l 등)은 '바뀌었으면 일단 기록'으로 처리해 데이터를 잃지 않는다.
    // -------------------------------------------------------------------------
    private boolean improved(Current cur, int score, String medal,
                             Map<String, Short> rankByEagate, Map<String, String> codeByEagate) {
        if (score > cur.score()) return true;
        if (Objects.equals(cur.medalCode(), medal)) return false;

        Short newRank = rankOf(medal, rankByEagate, codeByEagate);
        Short oldRank = rankOf(cur.medalCode(), rankByEagate, codeByEagate);
        if (newRank != null && oldRank != null) return newRank > oldRank;

        return true;   // 등급 미상 → 변경 사실만 남긴다
    }

    private Short rankOf(String code, Map<String, Short> rankByEagate, Map<String, String> codeByEagate) {
        if (code == null) return null;
        for (Map.Entry<String, String> e : codeByEagate.entrySet()) {
            if (e.getValue().equals(code)) return rankByEagate.get(e.getKey());
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // 마스터 upsert
    // -------------------------------------------------------------------------
    private void upsertSongs(List<ScoreRow> rows) {
        Map<String, ScoreRow> distinct = new LinkedHashMap<>();
        for (ScoreRow r : rows) distinct.putIfAbsent(r.eagateSongNo(), r);

        // version, bpm, duration 은 직접 시딩하는 값이므로 절대 덮어쓰지 않는다.
        batch("""
                INSERT INTO songs (eagate_song_no, title, genre, artist)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (eagate_song_no) DO UPDATE
                   SET title = EXCLUDED.title, genre = EXCLUDED.genre,
                       artist = EXCLUDED.artist, updated_at = now()
                """,
                distinct.values().stream()
                        .map(r -> new Object[]{ r.eagateSongNo(), r.title(), r.genre(), r.artist() })
                        .toList());
    }

    /** 보면이 '존재한다'는 사실만 등록한다. level/notes 는 시딩 값이므로 DO NOTHING. */
    private int upsertCharts(List<ScoreRow> rows, Map<String, Long> songIds) {
        Set<ChartKey> keys = new LinkedHashSet<>();
        for (ScoreRow r : rows) {
            Long songId = songIds.get(r.eagateSongNo());
            if (songId != null) keys.add(new ChartKey(songId, r.difficulty().name()));
        }

        int[] affected = batch("""
                INSERT INTO charts (song_id, difficulty)
                VALUES (?, ?::chart_difficulty)
                ON CONFLICT (song_id, difficulty) DO NOTHING
                """,
                keys.stream().map(k -> new Object[]{ k.songId(), k.difficulty() }).toList());

        return Arrays.stream(affected).map(n -> n > 0 ? 1 : 0).sum();
    }

    private void batchInsertScores(List<Object[]> inserts) {
        batch("""
                INSERT INTO score_records (user_id, chart_id, import_batch_id, score, medal_code)
                VALUES (?, ?, ?, ?, ?)
                """, inserts);
    }

    /**
     * 반드시 현재 트랜잭션의 커넥션을 써야 한다.
     * dataSource.getConnection() 으로 새 커넥션을 꺼내면, @Transactional 이 아직 커밋하지
     * 않은 import_batches 행이 그 커넥션에는 안 보여서 score_records 의 FK 가 깨진다.
     * DataSourceUtils.getConnection() 이 스프링 트랜잭션에 묶인 커넥션을 돌려준다.
     */
    private int[] batch(String sql, List<Object[]> args) {
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Object[] a : args) {
                for (int i = 0; i < a.length; i++) ps.setObject(i + 1, a[i]);
                ps.addBatch();
            }
            return ps.executeBatch();
        } catch (Exception e) {
            throw new IllegalStateException("batch failed: " + e.getMessage(), e);
        } finally {
            // 트랜잭션 커넥션이면 여기서 닫지 않는다(스프링이 관리). 아니면 반환한다.
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    // -------------------------------------------------------------------------
    // 조회 헬퍼
    // -------------------------------------------------------------------------
    private Map<String, Long> loadSongIds(List<ScoreRow> rows) {
        List<String> nos = rows.stream().map(ScoreRow::eagateSongNo).distinct().toList();
        Map<String, Long> map = new HashMap<>();
        jdbc.sql("SELECT eagate_song_no, id FROM songs WHERE eagate_song_no = ANY (?)")
                .param(nos.toArray(String[]::new))
                .query((rs, i) -> map.put(rs.getString(1), rs.getLong(2)))
                .list();
        return map;
    }

    private Map<ChartKey, Long> loadChartIds(Collection<Long> songIds) {
        Map<ChartKey, Long> map = new HashMap<>();
        if (songIds.isEmpty()) return map;
        jdbc.sql("SELECT id, song_id, difficulty::text FROM charts WHERE song_id = ANY (?)")
                .param(songIds.toArray(Long[]::new))
                .query((rs, i) -> map.put(new ChartKey(rs.getLong(2), rs.getString(3)), rs.getLong(1)))
                .list();
        return map;
    }

    private Map<Long, Current> loadCurrentBests(long userId, Collection<Long> chartIds) {
        Map<Long, Current> map = new HashMap<>();
        if (chartIds.isEmpty()) return map;
        jdbc.sql("""
                SELECT chart_id, score, medal_code
                  FROM user_chart_current
                 WHERE user_id = ? AND chart_id = ANY (?)
                """)
                .params(userId, chartIds.toArray(Long[]::new))
                .query((rs, i) -> map.put(rs.getLong(1), new Current(rs.getInt(2), rs.getString(3))))
                .list();
        return map;
    }

    private Map<String, Short> loadMedalRanks() {
        Map<String, Short> map = new HashMap<>();
        jdbc.sql("SELECT eagate_code, rank_order FROM clear_medals WHERE eagate_code IS NOT NULL")
                .query((rs, i) -> {
                    short r = rs.getShort(2);
                    return map.put(rs.getString(1), rs.wasNull() ? null : r);
                }).list();
        return map;
    }

    private Map<String, String> loadMedalCodes() {
        Map<String, String> map = new HashMap<>();
        jdbc.sql("SELECT eagate_code, code FROM clear_medals WHERE eagate_code IS NOT NULL")
                .query((rs, i) -> map.put(rs.getString(1), rs.getString(2))).list();
        return map;
    }

    // -------------------------------------------------------------------------
    // 체크섬: 순서 무관하도록 정렬 후 직렬화. 동일 스냅샷 재업로드를 잡는다.
    // -------------------------------------------------------------------------
    private String checksum(List<ScoreRow> rows) {
        StringBuilder sb = new StringBuilder();
        rows.stream()
                .map(r -> r.eagateSongNo() + "|" + r.difficulty() + "|" + r.score() + "|" + r.medalCode())
                .sorted()
                .forEach(sb::append);
        return sha256Hex(sb.toString());
    }

    static String sha256Hex(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : d) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16))
                        .append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String normalizeSource(String source) {
        return "MANUAL".equalsIgnoreCase(source) ? "MANUAL" : "BOOKMARKLET";
    }

    private record ChartKey(Long songId, String difficulty) {}
    private record Current(int score, String medalCode) {}
}