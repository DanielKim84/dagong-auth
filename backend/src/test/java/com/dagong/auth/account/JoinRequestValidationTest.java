package com.dagong.auth.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.dagong.auth.account.MembershipModels.JoinRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** 가입 입력은 앞뒤 공백을 지운 뒤 예전 수동 검사와 같은 기준으로 검사합니다. */
class JoinRequestValidationTest {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Test
    void 공백을_지우고_빈_이메일은_null로_본다() {
        JoinRequest request = new JoinRequest("  맛객  ", "   ");

        assertThat(request.displayName()).isEqualTo("맛객");
        assertThat(request.email()).isNull();
        assertThat(messages(request)).isEmpty();
    }

    @Test
    void 닉네임은_공백을_지운_뒤_1에서_80자다() {
        assertThat(messages(new JoinRequest(null, null))).containsExactly(MembershipModels.DISPLAY_NAME_MESSAGE);
        assertThat(messages(new JoinRequest("   ", null))).containsExactly(MembershipModels.DISPLAY_NAME_MESSAGE);
        assertThat(messages(new JoinRequest(" " + "가".repeat(80) + " ", null))).isEmpty();
        assertThat(messages(new JoinRequest("가".repeat(81), null)))
                .containsExactly(MembershipModels.DISPLAY_NAME_MESSAGE);
    }

    @Test
    void 이메일은_형식과_320자를_검사한다() {
        assertThat(messages(new JoinRequest("맛객", " taster@example.com "))).isEmpty();
        assertThat(messages(new JoinRequest("맛객", "taster@example"))).containsExactly(MembershipModels.EMAIL_MESSAGE);
        assertThat(messages(new JoinRequest("맛객", "a@" + "b".repeat(320) + ".com")))
                .containsExactly(MembershipModels.EMAIL_MESSAGE);
    }

    private static Set<String> messages(JoinRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }
}
