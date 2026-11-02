package com.dagong.auth.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 맛다공 가입 API의 요청·응답 자료형입니다. */
public final class MembershipModels {
    static final String DISPLAY_NAME_MESSAGE = "닉네임은 1~80자로 입력해 주세요.";
    static final String EMAIL_MESSAGE = "이메일 주소를 확인해 주세요.";

    private MembershipModels() {}

    /**
     * 맛다공 가입 상태입니다.
     *
     * @param status 맛다공 서비스 상태(svc_st). 가입 이력이 없으면 null입니다.
     * @param registered status가 ACTIVE일 때만 true입니다.
     */
    public record MembershipStatus(String displayName, String email, ServiceStatus status, boolean registered) {}

    /**
     * 가입 화면의 선택 입력입니다. 검사 전에 앞뒤 공백을 지우고, 빈 이메일은 입력하지 않은 것으로 봅니다.
     *
     * @param displayName 닉네임. 공백을 지운 뒤 1~80자여야 합니다.
     * @param email 연락처 이메일. 없으면 null입니다.
     */
    public record JoinRequest(
            @NotBlank(message = DISPLAY_NAME_MESSAGE) @Size(max = 80, message = DISPLAY_NAME_MESSAGE)
                    String displayName,
            @Size(max = 320, message = EMAIL_MESSAGE)
                    @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = EMAIL_MESSAGE)
                    String email) {
        public JoinRequest {
            displayName = displayName == null ? "" : displayName.trim();
            email = email == null || email.isBlank() ? null : email.trim();
        }
    }

    /** 가입 완료 응답입니다. JSON은 {"joined": true}입니다. */
    public record JoinResult(boolean joined) {}
}
