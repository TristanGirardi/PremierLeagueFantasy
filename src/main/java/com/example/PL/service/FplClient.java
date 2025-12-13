package com.example.PL.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class FplClient {

    private final WebClient fplWebClient;

    public FplClient(WebClient fplWebClient) {
        this.fplWebClient = fplWebClient;
    }

    public Mono<JsonNode> getBootstrapStatic() {
        return fplWebClient.get()
                .uri("/bootstrap-static/")
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    public Mono<JsonNode> getFixtures() {
        return fplWebClient.get()
                .uri("/fixtures/")
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    public Mono<JsonNode> getElementSummary(int playerId) {
        return fplWebClient.get()
                .uri("/element-summary/{id}/", playerId)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }
}
