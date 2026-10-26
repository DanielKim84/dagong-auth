package com.dagong.auth.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 맛다공과 여다공이 함께 사용하는 공통 계정입니다. */
@Entity
@Table(name = "accounts")
public class Account {
    @Id
    @Column(name = "user_id")
    private UUID id;

    // 이메일은 선택 연락처이며, 로그인 식별자나 고유 키로 사용하지 않습니다.
    @Column(length = 320)
    private String email;

    @Column(name = "email_vrf_dt")
    private OffsetDateTime emailVerifiedAt;

    @Column(name = "phone_no", length = 32)
    private String phoneNumber;

    @Column(name = "phone_vrf_dt")
    private OffsetDateTime phoneVerifiedAt;

    // 본인 확인 연동 시에도 CI 원문 대신 비밀키 기반 해시만 저장합니다.
    @Column(name = "ci_hash", length = 128)
    private String identityCiHash;

    @Column(name = "ci_vrf_dt")
    private OffsetDateTime identityVerifiedAt;

    @Column(name = "user_nm", nullable = false, length = 80)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_st", nullable = false, length = 24)
    private AccountStatus status;

    @Column(name = "reg_dt", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "upd_dt", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "last_login_dt")
    private OffsetDateTime lastLoginAt;

    @Column(name = "del_req_dt")
    private OffsetDateTime deletionRequestedAt;

    @Column(name = "del_dt")
    private OffsetDateTime deletedAt;

    @Column(name = "block_dt")
    private OffsetDateTime blockedAt;

    @Column(name = "block_end_dt")
    private OffsetDateTime blockedUntil;

    @Column(name = "block_rsn", length = 500)
    private String blockReason;

    protected Account() {}

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public OffsetDateTime getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public OffsetDateTime getPhoneVerifiedAt() {
        return phoneVerifiedAt;
    }

    public String getIdentityCiHash() {
        return identityCiHash;
    }

    public OffsetDateTime getIdentityVerifiedAt() {
        return identityVerifiedAt;
    }

    public String getDisplayName() {
        return displayName;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public OffsetDateTime getDeletionRequestedAt() {
        return deletionRequestedAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public OffsetDateTime getBlockedAt() {
        return blockedAt;
    }

    public OffsetDateTime getBlockedUntil() {
        return blockedUntil;
    }

    public String getBlockReason() {
        return blockReason;
    }
}
