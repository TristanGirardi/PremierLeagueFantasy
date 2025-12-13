package com.example.PL.service;

import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Service
public class FplLookupService {
    private final FplClient fplClient;

    public FplLookupService(FplClient fplClient) {
        this.fplClient = fplClient;
    }

    public Mono<Map<Integer, String>> getTeamIdToNameMap() {
        return fplClient.getBootstrapStatic()
                .map(bootstrap -> {
                    Map<Integer, String> map = new HashMap<>();
                    for (JsonNode team : bootstrap.get("teams")) {
                        map.put(team.get("id").asInt(), team.get("name").asText());
                    }
                    return map;
                });
    }

    public Mono<Map<Integer, String>> getPlayerIdToNameMap() {
        return fplClient.getBootstrapStatic()
                .map(bootstrap -> {
                    Map<Integer, String> map = new HashMap<>();
                    for (JsonNode p : bootstrap.get("elements")) {
                        int id = p.get("id").asInt();
                        String name = p.get("web_name").asText(); // "Salah", "Haaland", etc.
                        map.put(id, name);
                    }
                    return map;
                });
    }

    public Mono<Map<Integer, String>> getPositionIdToNameMap() {
        return fplClient.getBootstrapStatic()
                .map(bootstrap -> {
                    Map<Integer, String> map = new HashMap<>();
                    for (JsonNode t : bootstrap.get("element_types")) {
                        map.put(t.get("id").asInt(), t.get("singular_name").asText()); // e.g., "Goalkeeper"
                    }
                    return map;
                });
    }

}
