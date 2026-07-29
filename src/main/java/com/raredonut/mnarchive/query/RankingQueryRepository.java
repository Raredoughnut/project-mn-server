package com.raredonut.mnarchive.query;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 플레이어 랭킹 조회. 순위 계산에 윈도우 함수가 필요해 네이티브 쿼리를 쓴다.
 *
 * 노출 대상 조건이 세 곳(목록·전체 수·내 순위)에 똑같이 들어가야 해서 {@link #VISIBLE_WHERE}
 * 하나로 묶었다. 조건이 어긋나면 "전체 42명인데 43위" 같은 값이 나온다.
 */
@Repository
public class RankingQueryRepository {

    /**
     * 랭킹에 나타날 사용자 조건.
     *
     * pop_class 가 null 이면 아직 임포트를 한 번도 하지 않은 사용자다. 공개에 동의했더라도
     * 순위를 매길 값이 없으니 제외한다. player_name 도 같은 이유로 제외하는데, 이쪽은
     * 표시할 이름이 없어서다.
     */
    private static final String VISIBLE_WHERE = """
        WHERE ranking_visible
          AND pop_class   IS NOT NULL
          AND player_name IS NOT NULL
        """;

    /**
     * 팝클래스가 같으면 같은 순위를 준다 — RANK() 라 1, 1, 3 이 된다.
     * 정렬은 (pop_class DESC, id) 로 고정한다. id 가 없으면 동점자 순서가 매번 달라져
     * 페이지를 넘길 때 같은 사람이 두 번 보이거나 누락된다.
     */
    private static final String RANKED_CTE = """
        WITH ranked AS (
            SELECT id, player_name, character_name, pop_class,
                   RANK() OVER (ORDER BY pop_class DESC) AS rank
            FROM   users
            """ + VISIBLE_WHERE + """
        )
        """;

    private final JdbcClient jdbc;

    public RankingQueryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<PlayerRankingView> findPage(long viewerId, int limit, int offset) {
        return jdbc.sql(RANKED_CTE + """
                SELECT rank,
                       player_name,
                       character_name,
                       pop_class,
                       (id = :viewerId) AS me
                FROM   ranked
                ORDER  BY rank, id
                LIMIT  :limit OFFSET :offset
                """)
                .param("viewerId", viewerId)
                .param("limit", limit)
                .param("offset", offset)
                .query(PlayerRankingView.class)
                .list();
    }

    public long countVisible() {
        return jdbc.sql("SELECT count(*) FROM users " + VISIBLE_WHERE)
                .query(Long.class).single();
    }

    /**
     * 내 순위. 공개에 동의하지 않았거나 아직 임포트하지 않았으면 비어 있다 —
     * 랭킹에 없는 사람에게는 순위도 없다.
     */
    public Optional<Long> findMyRank(long viewerId) {
        return jdbc.sql(RANKED_CTE + "SELECT rank FROM ranked WHERE id = :viewerId")
                .param("viewerId", viewerId)
                .query(Long.class)
                .optional();
    }
}
