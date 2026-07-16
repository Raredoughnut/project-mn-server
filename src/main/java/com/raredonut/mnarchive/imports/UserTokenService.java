package com.raredonut.mnarchive.imports;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * 북마클릿 토큰.
 *
 * 이 토큰은 사용자의 즐겨찾기에 평문으로 남는다. 즐겨찾기를 보는 사람은 누구나 볼 수 있다.
 *   - 권한은 POST /api/imports 하나로만 제한한다. 계정 삭제·설정 변경은 불가.
 *   - DB 에는 SHA-256 해시만 저장한다. 평문은 발급 응답에 단 한 번만 실린다.
 *   - 재발급하면 이전 토큰은 즉시 무효 (북마클릿을 다시 복사해야 함을 UI 에 명시할 것).
 *
 * 해시는 SHA-256 으로 충분하다. 256비트 랜덤이라 사전 공격 대상이 아니므로 bcrypt 는 불필요하고,
 * 매 업로드마다 비용만 든다.
 */
@Service
public class UserTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PREFIX = "mna_";

    private final JdbcClient jdbc;

    public UserTokenService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 평문 토큰을 반환한다. 이 값은 다시 조회할 수 없다. */
    @Transactional
    public String issue(long userId) {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        String plain = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(buf);

        jdbc.sql("UPDATE users SET api_token = ?, updated_at = now() WHERE id = ?")
                .params(ImportService.sha256Hex(plain), userId)
                .update();

        return plain;
    }

    @Transactional
    public void revoke(long userId) {
        jdbc.sql("UPDATE users SET api_token = NULL, updated_at = now() WHERE id = ?")
                .param(userId).update();
    }

    public Optional<Long> resolveUserId(String plainToken) {
        return jdbc.sql("SELECT id FROM users WHERE api_token = ?")
                .param(ImportService.sha256Hex(plainToken))
                .query(Long.class).optional();
    }
}
