package com.dagong.auth.security;

import com.dagong.auth.device.Device;
import com.dagong.auth.device.DeviceLoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공통 인증 서버의 SSO 세션을 끊습니다. 맛다공이 로그아웃할 때와, 토큰이 거부되거나 DB에서 계정이 사라져
 * SSO 세션만 남았을 때 호출해 다음 인가 요청에서 소셜 인증을 다시 받게 합니다.
 */
@RestController
public class AuthSessionController {
    private final DeviceLoginService deviceLogins;

    public AuthSessionController(DeviceLoginService deviceLogins) {
        this.deviceLogins = deviceLogins;
    }

    /**
     * @param endDeviceLogin 사용자가 직접 로그아웃할 때 true. 이 기기의 맛다공 인가 기록도 지워, 다른 기기에서 로그인할 때
     *     이 기기를 로그아웃할지 묻지 않게 합니다. 토큰 만료로 다시 로그인할 때는 같은 브라우저의 다른 탭 토큰을 살려 두려고 넘기지 않습니다.
     */
    @PostMapping("/auth/session/clear")
    public ResponseEntity<Void> clear(
            HttpServletRequest request, @RequestParam(defaultValue = "false") boolean endDeviceLogin) {
        if (endDeviceLogin) Device.cookieValue(request).ifPresent(deviceLogins::endThisDevice);
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }
}
