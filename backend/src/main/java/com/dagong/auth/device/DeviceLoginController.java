package com.dagong.auth.device;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 로그인 화면이 쓰는 한 기기 로그인 API입니다.
 * 확인 화면의 "계속"·"취소"와, 다른 기기 로그인으로 로그아웃됐다는 안내 확인을 맡습니다.
 */
@RestController
@RequestMapping("/auth/device")
public class DeviceLoginController {
    private final DeviceLoginService logins;

    public DeviceLoginController(DeviceLoginService logins) {
        this.logins = logins;
    }

    /** 다른 기기를 로그아웃하고 로그인을 이어 갑니다. 화면은 돌려받은 인가 주소로 이동합니다. */
    @PostMapping("/confirm")
    public Map<String, String> confirm(HttpServletRequest request) {
        HttpSession session = pendingSession(request);
        session.setAttribute(
                SingleDeviceLoginFilter.CONFIRMED, session.getAttribute(SingleDeviceLoginFilter.PENDING_CONFIRM));
        return Map.of("redirectUrl", takePendingUrl(session));
    }

    /**
     * 로그인하지 않고 돌아갑니다. SSO 세션을 끊은 뒤 같은 인가 주소로 보내면 처음 로그인 화면이 다시 열리고,
     * 다른 계정으로 로그인하거나 나중에 다시 시도할 수 있습니다.
     */
    @PostMapping("/cancel")
    public Map<String, String> cancel(HttpServletRequest request) {
        HttpSession session = pendingSession(request);
        String redirectUrl = takePendingUrl(session);
        session.invalidate();
        SecurityContextHolder.clearContext();
        return Map.of("redirectUrl", redirectUrl);
    }

    /** 이 기기가 다른 기기 로그인으로 로그아웃됐는지 알려 주고, 한 번만 보이도록 표시를 지웁니다. */
    @GetMapping("/notice")
    public Map<String, Boolean> notice(HttpServletRequest request) {
        boolean isSignedOut = false;
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(SingleDeviceLoginFilter.SIGNED_OUT_NOTICE) != null) {
            session.removeAttribute(SingleDeviceLoginFilter.SIGNED_OUT_NOTICE);
            isSignedOut = true;
        }
        // 토큰이 거부돼 맛다공이 SSO 세션을 먼저 끊고 들어온 경우에는 기기 표시만 남아 있습니다.
        boolean hasDeviceSignout =
                Device.cookieValue(request).map(logins::consumeSignout).orElse(false);
        return Map.of("signedOutByOtherDevice", isSignedOut || hasDeviceSignout);
    }

    private static HttpSession pendingSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SingleDeviceLoginFilter.PENDING_AUTHORIZE_URL) == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "확인할 로그인이 없어요. 처음부터 다시 로그인해 주세요.");
        }
        return session;
    }

    private static String takePendingUrl(HttpSession session) {
        String url = (String) session.getAttribute(SingleDeviceLoginFilter.PENDING_AUTHORIZE_URL);
        session.removeAttribute(SingleDeviceLoginFilter.PENDING_AUTHORIZE_URL);
        session.removeAttribute(SingleDeviceLoginFilter.PENDING_CONFIRM);
        return url;
    }
}
