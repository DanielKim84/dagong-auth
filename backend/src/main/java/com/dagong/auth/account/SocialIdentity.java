package com.dagong.auth.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 제공자의 고유 사용자 ID를 공통 계정에 연결합니다. 제공자 이메일은 참고 정보로만 사용합니다. */
@Entity
@Table(name = "social_identities")
public class SocialIdentity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "provider_user_id", nullable = false, length = 255)
    private String providerUserId;

    @Column(name = "provider_email", length = 320)
    private String providerEmail;

    @Column(name = "provider_email_verified", nullable = false)
    private boolean providerEmailVerified;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "last_authenticated_at")
    private OffsetDateTime lastAuthenticatedAt;

    @Column(name = "disconnected_at")
    private OffsetDateTime disconnectedAt;

    protected SocialIdentity() {}

    public UUID getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderUserId() {
        return providerUserId;
    }

    public String getProviderEmail() {
        return providerEmail;
    }

    public boolean isProviderEmailVerified() {
        return providerEmailVerified;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getLastAuthenticatedAt() {
        return lastAuthenticatedAt;
    }

    public OffsetDateTime getDisconnectedAt() {
        return disconnectedAt;
    }
}
