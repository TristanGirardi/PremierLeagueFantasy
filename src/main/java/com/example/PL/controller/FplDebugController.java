package com.example.PL.controller;

import com.example.PL.service.FplClient;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/fpl")
@RequiredArgsConstructor
public class FplDebugController {

    private final FplClient fplClient;

    @GetMapping("/bootstrap")
    public Mono<JsonNode> bootstrap() {
        return fplClient.getBootstrapStatic();
    }

    @GetMapping("/fixtures")
    public Mono<JsonNode> fixtures() {
        return fplClient.getFixtures();
    }

    @GetMapping("/player/{id}")
    public Mono<JsonNode> player(@PathVariable int id) {
        return fplClient.getElementSummary(id);
    }

}
