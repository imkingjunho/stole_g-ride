package com.gachiga.route;

import com.gachiga.common.response.ApiResponse;
import com.gachiga.contract.route.HubInfo;
import com.gachiga.contract.route.HubPort;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 출발 거점 프리셋 API. */
@RestController
@RequestMapping("/api/hubs")
@Profile("!test")
public class HubController {

    private final HubPort hubPort;

    public HubController(HubPort hubPort) {
        this.hubPort = hubPort;
    }

    @GetMapping
    public ApiResponse<List<HubInfo>> findAll() {
        return ApiResponse.ok(hubPort.findAll());
    }
}
