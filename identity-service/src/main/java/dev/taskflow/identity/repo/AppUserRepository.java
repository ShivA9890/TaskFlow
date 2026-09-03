package dev.taskflow.identity.repo;

import dev.taskflow.identity.domain.AppUser;
import dev.taskflow.identity.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    @Query("select u from AppUser u where lower(u.email) = lower(:email)")
    Optional<AppUser> findByEmailIgnoreCase(String email);

    @Query("select count(u) > 0 from AppUser u where lower(u.email) = lower(:email)")
    boolean existsByEmailIgnoreCase(String email);

    List<AppUser> findAllByOrganizationIdOrderByNameAsc(UUID orgId);

    List<AppUser> findAllByOrganizationIdAndRole(UUID orgId, Role role);

}
