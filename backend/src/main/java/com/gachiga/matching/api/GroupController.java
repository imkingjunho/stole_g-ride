package com.gachiga.matching.api;

import com.gachiga.matching.domain.MatchGroup;
import com.gachiga.matching.port.MatchGroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final MatchGroupRepository groupRepository;

    @GetMapping("/{id}")
    public ResponseEntity<GroupResponse> getGroup(@PathVariable Long id) {
        return groupRepository.findById(id)
            .map(group -> ResponseEntity.ok(GroupResponse.from(group)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<GroupResponse> completeGroup(@PathVariable Long id) {
        return groupRepository.findById(id)
            .map(group -> {
                group.complete();
                MatchGroup saved = groupRepository.save(group);
                log.info("Group {} completed", id);
                return ResponseEntity.ok(GroupResponse.from(saved));
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}