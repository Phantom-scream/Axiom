package com.axiom.persistence.entity;

import com.axiom.domain.pipeline.CiProviderType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "repositories", uniqueConstraints = @UniqueConstraint(name = "uq_repositories_provider_owner_name", columnNames = {"provider", "owner", "name"}))
public class RepositoryEntity {
    @Id private UUID id;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private CiProviderType provider;
    @Column(nullable = false) private String owner;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    protected RepositoryEntity() {}
    public RepositoryEntity(UUID id, CiProviderType provider, String owner, String name, Instant createdAt, Instant updatedAt) { this.id = id; this.provider = provider; this.owner = owner; this.name = name; this.createdAt = createdAt; this.updatedAt = updatedAt; }
    public UUID getId() { return id; }
}
