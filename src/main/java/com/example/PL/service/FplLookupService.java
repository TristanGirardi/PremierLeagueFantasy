package com.example.PL.service;

import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
                    JsonNode types = bootstrap.get("element_types");
                    for (int i = 0; i < types.size(); i++) {
                        JsonNode t = types.get(i);
                        map.put(t.get("id").asInt(), t.get("singular_name").asText()); // "Goalkeeper"
                    }
                    return map;
                });
    }

    public Mono<Map<Integer, String>> getPlayerIdToWebNameMap() {
        return fplClient.getBootstrapStatic()
                .map(bootstrap -> {
                    Map<Integer, String> map = new HashMap<>();
                    JsonNode players = bootstrap.get("elements");
                    for (int i = 0; i < players.size(); i++) {
                        JsonNode p = players.get(i);
                        map.put(p.get("id").asInt(), p.get("web_name").asText());
                    }
                    return map;
                });
    }

    public Mono<Map<Integer, Integer>> getPlayerIdToPositionIdMap() {
        return fplClient.getBootstrapStatic()
                .map(bootstrap -> {
                    Map<Integer, Integer> map = new HashMap<>();
                    JsonNode players = bootstrap.get("elements");
                    for (int i = 0; i < players.size(); i++) {
                        JsonNode p = players.get(i);
                        map.put(p.get("id").asInt(), p.get("element_type").asInt());
                    }
                    return map;
                });
    }

    public Mono<List<Integer>> getAllPlayerIds() {
        return fplClient.getBootstrapStatic()
                .map(bootstrap -> {
                    JsonNode players = bootstrap.get("elements");
                    List<Integer> ids = new ArrayList<>(players.size());
                    for (int i = 0; i < players.size(); i++) {
                        ids.add(players.get(i).get("id").asInt());
                    }
                    return ids;
                });

    }
}
