package com.axiom.api.controller;

import com.axiom.api.dto.HealthResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {
    private final String version;
    public HealthController(@Value("${info.app.version}") String version) { this.version = version; }
    @GetMapping public HealthResponse health() { return new HealthResponse("UP", version); }
}
