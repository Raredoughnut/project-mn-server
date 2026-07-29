package com.raredonut.mnarchive.me;

import com.raredonut.mnarchive.config.MnUser;
import com.raredonut.mnarchive.domain.User;
import com.raredonut.mnarchive.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 랭킹 노출 설정 변경. 컨텍스트도 DB 도 없이 컨트롤러만 세운다.
 *
 * DB 반영(트랜잭션 안에서 더티 체킹이 UPDATE 를 날리는지)까지는 여기서 확인할 수 없다.
 * 그건 실제 PostgreSQL 로 따로 확인했고, 자동화하려면 Testcontainers 가 필요하다.
 */
class MeControllerTest {

    private MockMvc mvc;
    private User user;

    @BeforeEach
    void setUp() {
        user = User.ofGoogle("sub-1", "me@example.com");

        UserRepository users = mock(UserRepository.class);
        when(users.findById(anyLong())).thenReturn(Optional.of(user));

        mvc = MockMvcBuilders.standaloneSetup(new MeController(users))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        MnUser principal = new MnUser(
                AuthorityUtils.createAuthorityList("ROLE_USER"),
                new OidcIdToken("t", Instant.now(), Instant.now().plusSeconds(600),
                        Map.of("sub", "sub-1")),
                null, 1L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 기본값은_비공개다() {
        assertThat(user.isRankingVisible()).isFalse();
    }

    @Test
    void 공개로_켠다() throws Exception {
        mvc.perform(put("/api/me/ranking-visibility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visible\":true}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"visible\":true}"));

        assertThat(user.isRankingVisible()).isTrue();
    }

    @Test
    void 다시_비공개로_끈다() throws Exception {
        user.changeRankingVisibility(true);

        mvc.perform(put("/api/me/ranking-visibility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visible\":false}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"visible\":false}"));

        assertThat(user.isRankingVisible()).isFalse();
    }

    /** visible 을 빠뜨린 요청이 조용히 false 로 처리되면 안 된다 — 의도치 않은 비공개가 된다. */
    @Test
    void visible_이_없으면_400() throws Exception {
        mvc.perform(put("/api/me/ranking-visibility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        assertThat(user.isRankingVisible()).isFalse();
    }
}
