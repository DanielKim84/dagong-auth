package com.dagong.auth.device;

import java.util.regex.Pattern;

/**
 * 한 기기 로그인에서 나누는 기기 종류입니다. 휴대폰 1대와 PC 1대까지 동시에 로그인할 수 있습니다.
 * 웹에서는 브라우저 정보(User-Agent)로 나누므로 태블릿은 휴대폰으로 봅니다. 앱을 내면 앱이 보내는 값으로 바꿉니다.
 */
public enum DeviceType {
    MOBILE("휴대폰"),
    PC("PC");

    private static final Pattern MOBILE_AGENT = Pattern.compile("(?i)mobi|android|iphone|ipad|ipod|tablet");

    private final String label;

    DeviceType(String label) {
        this.label = label;
    }

    /** 화면 문구에 쓰는 이름(휴대폰, PC)입니다. */
    public String label() {
        return label;
    }

    public static DeviceType fromUserAgent(String userAgent) {
        return userAgent != null && MOBILE_AGENT.matcher(userAgent).find() ? MOBILE : PC;
    }
}
