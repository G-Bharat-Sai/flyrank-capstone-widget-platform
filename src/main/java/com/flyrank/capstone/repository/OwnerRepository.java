package com.flyrank.capstone.repository;

import com.flyrank.capstone.entity.Owner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OwnerRepository extends JpaRepository<Owner, UUID> {
    Optional<Owner> findByEmail(String email);
    boolean existsByEmail(String email);
}
