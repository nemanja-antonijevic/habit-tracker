package com.nantonijevic.habits.client;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "api_clients")
public class ApiClient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
        name = "api_key_hash",
        nullable = false,
        unique = true,
        length = 64,
        columnDefinition = "char(64)"
    )
    private String apiKeyHash;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(
        name = "tier",
        nullable = false,
        length = 32
    )
    private ClientTier tier;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ApiClient() {
    }

    public ApiClient(
        String apiKeyHash,
        ClientTier tier,
        String name,
        Instant createdAt
    ) {
        this.apiKeyHash = apiKeyHash;
        this.tier = tier;
        this.name = name;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getApiKeyHash() {
        return apiKeyHash;
    }

    public ClientTier getTier() {
        return tier;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
