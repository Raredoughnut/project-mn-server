package com.raredonut.mnarchive.config;

import com.raredonut.mnarchive.domain.User;
import com.raredonut.mnarchive.domain.UserRepository;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Google 로그인 성공 시점에 users 행을 찾거나 만든다.
 *
 * 키는 sub 다. 이메일이 아니다 — 구글 계정의 이메일은 바뀔 수 있고, 그때 사용자가 자기
 * 스코어 이력을 통째로 잃으면 안 된다. sub 는 영구 불변이다.
 */
@Service
public class MnOidcUserService extends OidcUserService {

    private final UserRepository users;

    public MnOidcUserService(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        OidcUser oidc = super.loadUser(request);

        String googleId = oidc.getSubject();
        String email = oidc.getEmail();

        User user = users.findByGoogleId(googleId)
                .map(u -> { u.syncEmail(email); return u; })
                .orElseGet(() -> users.save(User.ofGoogle(googleId, email)));

        return new MnUser(oidc.getAuthorities(), oidc.getIdToken(), oidc.getUserInfo(), user.getId());
    }
}
