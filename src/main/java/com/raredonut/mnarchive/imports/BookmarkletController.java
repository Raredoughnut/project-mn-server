package com.raredonut.mnarchive.imports;

import com.raredonut.mnarchive.config.MnUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 북마클릿 발급.
 *
 * same-origin 요청이므로 세션 쿠키로 인증된다(/api/imports 와 정반대다).
 * 토큰이 이미 박힌 완성된 javascript: 문자열을 통째로 내려주고, 프론트는 그것을
 * <a href="{bookmarklet}"> 로 렌더해 즐겨찾기 바에 드래그하게 한다.
 * 사용자는 토큰을 볼 일도, 복사할 일도 없다.
 */
@RestController
@RequestMapping("/api/me/bookmarklet")
public class BookmarkletController {

    private final UserTokenService tokenService;
    private final String cdnUrl;
    private final String apiBase;

    public BookmarkletController(UserTokenService tokenService,
                                 @Value("${app.cdn-url}") String cdnUrl,
                                 @Value("${app.api-base}") String apiBase) {
        this.tokenService = tokenService;
        this.cdnUrl = cdnUrl;
        this.apiBase = apiBase;
    }

    /**
     * 토큰을 발급(재발급)하고 북마클릿 문자열을 반환한다.
     * 재발급하면 이전 북마클릿은 즉시 무효가 된다 — UI 에 반드시 명시할 것.
     */
    @PostMapping
    public BookmarkletResponse issue(@AuthenticationPrincipal MnUser user) {
        String plainToken = tokenService.issue(user.userId());   // 평문은 여기서만 존재한다

        // ?v= 로 캐시를 무효화한다. 북마클릿은 한 번 저장되면 사용자가 다시 손대지 않으므로,
        // 셀렉터를 고쳐도 CDN 캐시 때문에 반영이 안 되는 사고를 막는다.
        String js = """
                javascript:(function(){\
                var s=document.createElement('script');\
                s.src='%s?v='+Date.now();\
                s.onload=function(){post('%s',0,'%s',false)};\
                document.head.appendChild(s);})();""".formatted(cdnUrl, apiBase, plainToken);

        return new BookmarkletResponse(js);
    }

    @DeleteMapping
    public void revoke(@AuthenticationPrincipal MnUser user) {
        tokenService.revoke(user.userId());
    }

    public record BookmarkletResponse(String bookmarklet) {}
}
