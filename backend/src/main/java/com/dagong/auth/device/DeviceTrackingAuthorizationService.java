package com.dagong.auth.device;

import java.util.UUID;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * 인가 기록 저장소를 감싸, 맛다공 인가 코드를 처음 저장할 때 어느 기기의 로그인인지 함께 남깁니다.
 * 기기 정보는 같은 인가 요청에서 {@link SingleDeviceLoginFilter}가 요청에 담아 둔 값을 씁니다.
 * 토큰 교환 등 그 밖의 저장·조회는 그대로 넘깁니다.
 */
public class DeviceTrackingAuthorizationService implements OAuth2AuthorizationService {
    private final OAuth2AuthorizationService delegate;
    private final RegisteredClientRepository clients;
    private final DeviceLoginService logins;
    private final String matdagongClientId;

    public DeviceTrackingAuthorizationService(
            OAuth2AuthorizationService delegate,
            RegisteredClientRepository clients,
            DeviceLoginService logins,
            String matdagongClientId) {
        this.delegate = delegate;
        this.clients = clients;
        this.logins = logins;
        this.matdagongClientId = matdagongClientId;
    }

    @Override
    public void save(OAuth2Authorization authorization) {
        delegate.save(authorization);
        Device device = currentDevice();
        if (device == null || authorization.getToken(OAuth2AuthorizationCode.class) == null) return;
        RegisteredClient client = clients.findById(authorization.getRegisteredClientId());
        if (client == null || !matdagongClientId.equals(client.getClientId())) return;
        try {
            logins.record(authorization.getId(), UUID.fromString(authorization.getPrincipalName()), device);
        } catch (IllegalArgumentException ignored) {
            // 주체가 계정 UUID가 아닌 인가(클라이언트 자체 인증 등)는 기기 로그인으로 세지 않습니다.
        }
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        delegate.remove(authorization);
    }

    @Override
    public OAuth2Authorization findById(String id) {
        return delegate.findById(id);
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        return delegate.findByToken(token, tokenType);
    }

    private static Device currentDevice() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) return null;
        Object device =
                attributes.getAttribute(SingleDeviceLoginFilter.DEVICE_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        return device instanceof Device found ? found : null;
    }
}
