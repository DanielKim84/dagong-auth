package com.dagong.auth.account;

/** 맛다공과 여다공 전체에 적용되는 계정 상태입니다. */
public enum AccountStatus {
    /** 로그인과 가입된 서비스 이용이 가능한 상태입니다. */
    ACTIVE,

    /** 관리자가 모든 서비스에서 로그인을 차단한 상태입니다. */
    BLOCKED,

    /** 탈퇴 처리를 시작했지만 데이터 삭제 또는 익명화가 끝나지 않은 상태입니다. */
    DELETION_PENDING,

    /** 탈퇴 처리가 끝났으며, 이력 보존을 위해 계정 행은 남아 있는 상태입니다. */
    DELETED
}
