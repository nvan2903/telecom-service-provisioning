package com.nvan.telecomprovisioning.provisioning.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "idempotency_key_claim")
public class IdempotencyKeyClaim {

    @Id
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IdempotencyKeyClaim() {
    }

    public IdempotencyKeyClaim(String idempotencyKey) {
        this.idempotencyKey = requireText(idempotencyKey);
    }

    @PrePersist
    private void setCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    private static String requireText(String value) {
        Objects.requireNonNull(value, "idempotencyKey must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        return value;
    }
}
