package dev.taskflow.tasks.repo;

import dev.taskflow.tasks.domain.Board;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BoardRepository extends JpaRepository<Board, UUID> {

    List<Board> findAllByOrgIdOrderByNameAsc(UUID orgId);

    Optional<Board> findByIdAndOrgId(UUID id, UUID orgId);

    /**
     * Takes a row lock on the board. Every task move within a board serializes
     * on this, so two concurrent drops cannot compute the same position from
     * the same stale read.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Board b where b.id = :id and b.orgId = :orgId")
    Optional<Board> lockByIdAndOrgId(UUID id, UUID orgId);
}
