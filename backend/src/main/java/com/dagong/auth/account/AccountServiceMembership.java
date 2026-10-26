package com.dagong.auth.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 계정과 서비스마다 한 행을 두고, 가입·탈퇴·재가입에 따른 현재 상태를 관리합니다. */
@Entity
@Table(
        name = "account_services",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_account_services_user_svc",
                columnNames = {"user_id", "svc_cd"}))
public class AccountServiceMembership {
    @Id
    @Column(name = "membership_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(name = "svc_cd", nullable = false, length = 32)
    private ServiceCode serviceCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "svc_st", nullable = false, length = 24)
    private Status status;

    @Column(name = "join_dt", nullable = false)
    private OffsetDateTime joinedAt;

    @Column(name = "del_req_dt")
    private OffsetDateTime withdrawalRequestedAt;

    @Column(name = "del_dt")
    private OffsetDateTime withdrawnAt;

    @Column(name = "last_access_dt")
    private OffsetDateTime lastAccessedAt;

    protected AccountServiceMembership() {}

    /** 이 인증 서버를 사용하는 서비스 코드입니다. */
    public enum ServiceCode {
        MATDAGONG,
        YEODAGONG
    }

    /** 특정 서비스에서의 계정 이용 상태입니다. */
    public enum Status {
        ACTIVE,
        WITHDRAWAL_PENDING,
        WITHDRAWN,
        BLOCKED
    }
}
