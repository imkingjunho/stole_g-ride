package com.gachiga.matching.port;

import com.gachiga.matching.domain.MatchGroup;
import com.gachiga.matching.domain.GroupStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface MatchGroupRepository extends JpaRepository<MatchGroup, Long> {
    
    @Query("SELECT g FROM MatchGroup g WHERE g.status = 'PENDING' AND g.createdAt >= CURRENT_TIMESTAMP - INTERVAL 1 MINUTE")
    List<MatchGroup> findRecentPending();

    @Query("SELECT DISTINCT g FROM MatchGroup g JOIN g.members m WHERE m.userId = :userId ORDER BY g.createdAt DESC")
    List<MatchGroup> findByUserId(@Param("userId") Long userId);

    List<MatchGroup> findByHubId(Long hubId);

    List<MatchGroup> findByStatus(GroupStatus status);
}