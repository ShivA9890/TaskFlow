package dev.taskflow.identity.repo;

import dev.taskflow.identity.domain.Invite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InviteRepository extends JpaRepository<Invite, UUID> {

    Optional<Invite> findByTokenHash(String tokenHash);

    List<Invite> findAllByOrganizationIdOrderByCreatedAtDesc(UUID orgId);
}
