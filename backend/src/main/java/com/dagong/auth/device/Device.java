package com.dagong.auth.device;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

/**
 * 로그인한 기기입니다. 기기 ID는 인증 서버가 브라우저에 심은 쿠키(dagong_device)로, 같은 브라우저의 탭들은 같은 기기입니다.
 * 쿠키를 지우면 새 기기로 보며, 그때는 같은 종류의 이전 로그인을 끊을지 다시 묻습니다.
 */
public record Device(String id, DeviceType type) {
    static final String COOKIE_NAME = "dagong_device";
    private static final Duration COOKIE_MAX_AGE = Duration.ofDays(365);
    /** 우리가 만든 UUID 형식만 받습니다. 다른 값은 새 기기로 봅니다. */
    private static final Pattern DEVICE_ID = Pattern.compile("[0-9a-f-]{36}");

    /** 기기 쿠키가 있으면 읽고, 없으면 새로 만들어 응답에 심습니다. */
    static Device resolve(HttpServletRequest request, HttpServletResponse response) {
        DeviceType type = DeviceType.fromUserAgent(request.getHeader(HttpHeaders.USER_AGENT));
        Optional<String> existing = cookieValue(request);
        if (existing.isPresent()) return new Device(existing.get(), type);
        String id = UUID.randomUUID().toString();
        // 화면 스크립트가 읽을 필요가 없어 HttpOnly로 두고, 맛다공·로그인 화면 주소에서 모두 오도록 경로는 / 입니다.
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, id)
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(COOKIE_MAX_AGE)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return new Device(id, type);
    }

    /** 새로 만들지 않고 이미 있는 기기 쿠키만 읽습니다(로그아웃·안내 확인). */
    public static Optional<String> cookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return Optional.empty();
        return Arrays.stream(cookies)
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && DEVICE_ID.matcher(value).matches())
                .findFirst();
    }
}
