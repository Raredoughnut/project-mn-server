package com.raredonut.mnarchive.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

/**
 * SPA 용 CSRF 토큰 처리기.
 *
 * Spring Security 의 기본 처리기는 {@link XorCsrfTokenRequestAttributeHandler} 다. 토큰을
 * 요청마다 다른 값으로 마스킹해 BREACH 공격을 막는데, 이게 SPA 와 어긋난다 —
 * 프론트는 쿠키에서 **원본** 토큰을 읽어 헤더에 실어 보내는데, 기본 처리기는 받은 값을
 * 마스킹된 것으로 보고 XOR 해제를 시도하다 실패한다.
 *
 * 그래서 방향에 따라 다르게 처리한다.
 *   - 내보낼 때(handle)          : Xor 에 위임. 서버 렌더링 쪽에서 쓸 일이 생겨도 보호가 유지된다.
 *   - 헤더로 받을 때(resolve)     : 원본 그대로 비교. 쿠키에서 읽은 값이라 마스킹돼 있지 않다.
 *   - 폼 파라미터로 받을 때        : Xor 에 위임. 이쪽은 마스킹된 값이 온다.
 *
 * {@code XorCsrfTokenRequestAttributeHandler} 가 final 이라 상속이 아니라 위임으로 짰다.
 */
final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final XorCsrfTokenRequestAttributeHandler xor = new XorCsrfTokenRequestAttributeHandler();

    SpaCsrfTokenRequestHandler() {
        // 지연 로딩을 끈다. 켜 두면 토큰을 실제로 꺼내 쓰는 쪽이 없을 때 쿠키가 발급되지
        // 않는데, SPA 는 아무도 토큰을 '렌더링'하지 않으므로 영영 쿠키를 받지 못한다.
        this.xor.setCsrfRequestAttributeName(null);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       Supplier<CsrfToken> csrfToken) {
        this.xor.handle(request, response, csrfToken);
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        if (StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()))) {
            // 헤더로 왔다 = 쿠키에서 읽은 원본. 인터페이스 기본 구현이 값을 그대로 돌려준다.
            return CsrfTokenRequestHandler.super.resolveCsrfTokenValue(request, csrfToken);
        }
        return this.xor.resolveCsrfTokenValue(request, csrfToken);
    }
}
