package com.raredonut.mnarchive.query;

import com.raredonut.mnarchive.config.MnUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 페이지 파라미터 처리. 상한을 안 걸면 size=100000 한 방으로 전체 사용자를 긁어갈 수 있고,
 * 음수 page 는 음수 OFFSET 이 되어 SQL 이 터진다.
 */
class RankingControllerTest {

    private final RankingQueryRepository repo = mock(RankingQueryRepository.class);
    private final RankingController controller = new RankingController(repo);

    private final MnUser user = new MnUser(
            AuthorityUtils.createAuthorityList("ROLE_USER"),
            new OidcIdToken("t", Instant.now(), Instant.now().plusSeconds(600),
                    Map.of("sub", "sub-1")),
            null, 1L);

    private int[] captureLimitAndOffset(int page, int size) {
        when(repo.findPage(anyLong(), anyInt(), anyInt())).thenReturn(List.of());
        when(repo.findMyRank(anyLong())).thenReturn(Optional.empty());

        controller.players(user, page, size);

        ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> offset = ArgumentCaptor.forClass(Integer.class);
        verify(repo).findPage(anyLong(), limit.capture(), offset.capture());
        return new int[]{limit.getValue(), offset.getValue()};
    }

    @Test
    void 기본_페이지() {
        assertThat(captureLimitAndOffset(0, 50)).containsExactly(50, 0);
    }

    @Test
    void offset_은_페이지와_크기의_곱이다() {
        assertThat(captureLimitAndOffset(3, 20)).containsExactly(20, 60);
    }

    @Test
    void size_는_200_으로_잘린다() {
        assertThat(captureLimitAndOffset(0, 100_000)).containsExactly(200, 0);
    }

    @Test
    void size_0_이하는_1_로_올린다() {
        assertThat(captureLimitAndOffset(0, 0)).containsExactly(1, 0);
    }

    /** 음수 OFFSET 은 PostgreSQL 에서 에러다. 0 으로 눌러야 한다. */
    @Test
    void 음수_페이지는_0_으로_눌린다() {
        assertThat(captureLimitAndOffset(-5, 50)).containsExactly(50, 0);
    }

    @Test
    void 내_순위가_없으면_null_로_나간다() {
        when(repo.findPage(anyLong(), anyInt(), anyInt())).thenReturn(List.of());
        when(repo.findMyRank(anyLong())).thenReturn(Optional.empty());
        when(repo.countVisible()).thenReturn(0L);

        assertThat(controller.players(user, 0, 50).myRank()).isNull();
    }

    @Test
    void 내_순위가_있으면_그대로_나간다() {
        when(repo.findPage(anyLong(), anyInt(), anyInt())).thenReturn(List.of());
        when(repo.findMyRank(anyLong())).thenReturn(Optional.of(7L));
        when(repo.countVisible()).thenReturn(42L);

        var response = controller.players(user, 0, 50);
        assertThat(response.myRank()).isEqualTo(7L);
        assertThat(response.total()).isEqualTo(42L);
    }
}
