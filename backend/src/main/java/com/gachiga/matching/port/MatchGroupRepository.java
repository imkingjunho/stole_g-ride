package com.gachiga.matching.port;

import com.gachiga.matching.domain.MatchGroup;
import com.gachiga.matching.domain.GroupStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MatchGroupRepository extends JpaRepository<MatchGroup, Long> {
    
    @Query("SELECT g FROM MatchGroup g WHERE g.status = 'PENDING' ORDER BY g.createdAt DESC")
    List<MatchGroup> findRecentPending();
}