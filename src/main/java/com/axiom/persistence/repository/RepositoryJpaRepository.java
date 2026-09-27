package com.axiom.persistence.repository;

import com.axiom.persistence.entity.RepositoryEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositoryJpaRepository extends JpaRepository<RepositoryEntity, UUID> {}
