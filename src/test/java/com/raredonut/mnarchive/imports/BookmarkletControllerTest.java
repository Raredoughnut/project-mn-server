package com.raredonut.mnarchive.imports;

import com.raredonut.mnarchive.config.MnUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 북마클릿 문자열 조립.
 *
 * <p>이 문자열은 사용자의 즐겨찾기에 그대로 저장된다. 주소가 틀린 채로 나가면 서버 설정을
 * 고쳐도 이미 저장된 북마클릿은 옛 주소를 계속 쓰고, 사용자가 재발급받기 전까지 복구되지
 * 않는다. 되돌리기 비싼 값이라 여기서 못박아 둔다.
 */
class BookmarkletControllerTest {

    private final UserTokenService tokenService = mock(UserTokenService.class);

    private final MnUser user = new MnUser(
            AuthorityUtils.createAuthorityList("ROLE_USER"),
            new OidcIdToken("t", Instant.now(), Instant.now().plusSeconds(600),
                    Map.of("sub", "sub-1")),
            null, 1L);

    private String issue(String cdnUrl, String apiBase) {
        when(tokenService.issue(anyLong())).thenReturn("mna_TESTTOKEN");
        return new BookmarkletController(tokenService, cdnUrl, apiBase).issue(user).bookmarklet();
    }

    @Test
    void 로컬_설정이면_로컬_주소가_박힌다() {
        String js = issue("http://localhost:9000/popn-sync.js", "http://localhost:8080");

        assertThat(js).startsWith("javascript:");
        assertThat(js).contains("http://localhost:9000/popn-sync.js");
        assertThat(js).contains("'http://localhost:8080'");
        assertThat(js).doesNotContain("raredonut.com");
    }

    @Test
    void 운영_설정이면_운영_주소가_박힌다() {
        String js = issue("https://cdn.raredonut.com/popn-sync.js", "https://api.raredonut.com");

        assertThat(js).contains("https://cdn.raredonut.com/popn-sync.js");
        assertThat(js).contains("'https://api.raredonut.com'");
        assertThat(js).doesNotContain("localhost");
    }

    @Test
    void 토큰이_문자열에_들어간다() {
        assertThat(issue("https://cdn.example/s.js", "https://api.example"))
                .contains("'mna_TESTTOKEN'");
    }

    /** CDN 캐시 때문에 스크립트를 고쳐도 반영이 안 되는 사고를 막는 장치다. */
    @Test
    void 캐시_무효화_파라미터가_붙는다() {
        assertThat(issue("https://cdn.example/s.js", "https://api.example"))
                .contains("?v='+Date.now()");
    }
}
