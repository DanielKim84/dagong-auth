package com.dagong.auth.device;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 맛다공 인가 요청(GET /oauth2/authorize?client_id=맛다공)에서 한 기기 로그인을 적용합니다.
 * 소셜 로그인 직후뿐 아니라 다른 서비스에서 만든 SSO 세션으로 바로 인가 코드를 받는 경우도 여기를 지납니다.
 *
 * <ol>
 *   <li>다른 기기 로그인으로 로그아웃된 기기면 SSO 세션도 끊어 로그인 화면부터 다시 보게 합니다(안내 한 줄 표시).
 *   <li>같은 종류의 다른 기기에 로그인이 남아 있으면, 그 기기를 로그아웃할지 로그인 화면에서 먼저 묻습니다.
 *   <li>확인했거나 다른 기기가 없으면 같은 종류의 다른 기기 로그인을 끊고 인가 코드를 발급하게 둡니다.
 * </ol>
 */
public class SingleDeviceLoginFilter extends OncePerRequestFilter {
    /** 인가 코드가 발급될 때 어느 기기인지 남기도록 요청에 담아 두는 이름입니다. */
    public static final String DEVICE_ATTRIBUTE = SingleDeviceLoginFilter.class.getName() + ".DEVICE";

    static final String PENDING_AUTHORIZE_URL = SingleDeviceLoginFilter.class.getName() + ".PENDING_URL";
    /** 확인 화면이 기다리는 확인 값(계정:기기 종류)입니다. "계속"을 누르면 CONFIRMED로 옮깁니다. */
    static final String PENDING_CONFIRM = SingleDeviceLoginFilter.class.getName() + ".PENDING_CONFIRM";

    static final String CONFIRMED = SingleDeviceLoginFilter.class.getName() + ".CONFIRMED";
    static final String SIGNED_OUT_NOTICE = SingleDeviceLoginFilter.class.getName() + ".SIGNED_OUT";

    private final DeviceLoginService logins;
    private final String matdagongClientId;
    private final String loginPageUrl;

    public SingleDeviceLoginFilter(DeviceLoginService logins, String matdagongClientId, String loginPageUrl) {
        this.logins = logins;
        this.matdagongClientId = matdagongClientId;
        this.loginPageUrl = loginPageUrl;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"GET".equals(request.getMethod())
                || !"/oauth2/authorize".equals(request.getServletPath())
                || !matdagongClientId.equals(request.getParameter("client_id"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Device device = Device.resolve(request, response);
        request.setAttribute(DEVICE_ATTRIBUTE, device);
        UUID accountId = signedInAccount();
        if (accountId == null) {
            // 로그인 전 요청은 소셜 로그인을 마친 뒤 같은 인가 요청으로 다시 들어옵니다.
            chain.doFilter(request, response);
            return;
        }

        if (logins.consumeSignout(device.id())) {
            // 이 기기는 다른 기기 로그인으로 로그아웃됐습니다. SSO 세션까지 끊으면 인가 요청이 로그인 화면으로 이어지고,
            // 새 세션에 남긴 표시로 로그인 화면이 안내를 한 번 보여 줍니다.
            HttpSession oldSession = request.getSession(false);
            if (oldSession != null) oldSession.invalidate();
            SecurityContextHolder.clearContext();
            request.getSession(true).setAttribute(SIGNED_OUT_NOTICE, Boolean.TRUE);
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(true);
        String confirmKey = accountId + ":" + device.type().name();
        boolean isConfirmed = confirmKey.equals(session.getAttribute(CONFIRMED));
        if (!isConfirmed && logins.hasOtherActiveLogin(accountId, device)) {
            session.setAttribute(PENDING_AUTHORIZE_URL, request.getRequestURL() + "?" + request.getQueryString());
            session.setAttribute(PENDING_CONFIRM, confirmKey);
            response.sendRedirect(confirmPageUrl(device.type()));
            return;
        }
        // 확인은 이번 한 번만 씁니다. 남겨 두면 나중에 다른 기기가 다시 로그인했을 때 묻지 않고 끊게 됩니다.
        session.removeAttribute(CONFIRMED);
        logins.endOtherLogins(accountId, device);
        chain.doFilter(request, response);
    }

    private String confirmPageUrl(DeviceType type) {
        return loginPageUrl + (loginPageUrl.contains("?") ? "&" : "?") + "device-confirm=" + type.name();
    }

    private static UUID signedInAccount() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()) {
            return null;
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
