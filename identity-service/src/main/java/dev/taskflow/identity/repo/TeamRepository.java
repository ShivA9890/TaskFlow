package dev.taskflow.identity.repo;

import dev.taskflow.identity.domain.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {

    List<Team> findAllByOrganizationIdOrderByNameAsc(UUID orgId);

    @Query("select t from Team t join t.memberIds m where t.organization.id = :orgId and m = :userId")
    List<Team> findAllForMember(UUID orgId, UUID userId);
}
