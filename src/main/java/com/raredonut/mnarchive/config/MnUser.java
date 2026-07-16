package com.raredonut.mnarchive.config;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import java.util.Collection;

/**
 * 인증된 사용자. 우리 DB 의 users.id 를 들고 다닌다.
 *
 * 이게 없으면 컨트롤러마다 google sub 로 users 를 다시 조회해야 한다.
 * @AuthenticationPrincipal MnUser 로 주입받아 바로 userId() 를 쓴다.
 */
public class MnUser extends DefaultOidcUser {

    private final Long userId;

    public MnUser(Collection<? extends GrantedAuthority> authorities,
                  OidcIdToken idToken, OidcUserInfo userInfo, Long userId) {
        super(authorities, idToken, userInfo);
        this.userId = userId;
    }

    public Long userId() {
        return userId;
    }
}
