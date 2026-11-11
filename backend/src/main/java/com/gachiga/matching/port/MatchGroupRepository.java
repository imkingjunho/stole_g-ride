package com.gachiga.matching.port;

import com.gachiga.matching.domain.MatchGroup;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MatchGroupRepository extends JpaRepository<MatchGroup, Long> {

    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from MatchGroup g where g.id = :id")
    java.util.Optional<MatchGroup> findLockedById(
            @org.springframework.data.repository.query.Param("id") Long id);

    @Query("SELECT g FROM MatchGroup g WHERE g.status = 'PENDING' ORDER BY g.createdAt DESC")
    List<MatchGroup> findRecentPending();
}
