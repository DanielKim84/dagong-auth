package com.dagong.auth.account;

/** 공통 계정이 맛다공·여다공 각 서비스에서 갖는 가입 상태입니다(account_services.svc_st 값과 같습니다). */
public enum ServiceStatus {
    /** 서비스를 이용 중인 상태입니다. */
    ACTIVE,

    /** 서비스 탈퇴를 요청했고 정리가 끝나지 않은 상태입니다. 다시 가입할 수 있습니다. */
    WITHDRAWAL_PENDING,

    /** 서비스 탈퇴가 끝난 상태입니다. 다시 가입할 수 있습니다. */
    WITHDRAWN,

    /** 관리자가 이 서비스 이용만 막은 상태입니다. 다시 가입할 수 없습니다. */
    BLOCKED
}
