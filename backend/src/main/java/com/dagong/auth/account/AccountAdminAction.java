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
import java.time.OffsetDateTime;
import java.util.UUID;

/** 계정 전체 또는 서비스별 관리자 조치를 누적 기록합니다. */
@Entity
@Table(name = "account_admin_actions")
public class AccountAdminAction {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "actor_id", nullable = false, length = 160)
    private String actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Action action;

    @Column(length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "service_code", length = 32)
    private String serviceCode;

    protected AccountAdminAction() {}

    /** Administrator actions retained for investigation and appeal history. */
    /** 문의나 이의 제기를 확인할 수 있도록 기록하는 관리자 조치 종류입니다. */
    public enum Action {
        BLOCK,
        UNBLOCK,
        REQUEST_DELETION,
        COMPLETE_DELETION,
        RESTORE
    }
}
