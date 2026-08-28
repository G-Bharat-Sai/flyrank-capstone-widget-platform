package com.flyrank.capstone.repository;

import com.flyrank.capstone.entity.Widget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WidgetRepository extends JpaRepository<Widget, UUID> {
    List<Widget> findAllByOwnerId(UUID ownerId);
    Optional<Widget> findByIdAndOwnerId(UUID id, UUID ownerId);
}
