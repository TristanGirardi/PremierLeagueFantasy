package com.example.PL.controller;

import com.example.PL.dto.EnrichedFixtureDto;
import com.example.PL.dto.EnrichedPlayerDto;
import com.example.PL.service.FplClient;
import com.example.PL.service.FplLookupService;
import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;


import java.time.OffsetDateTime;
import java.util.*;

@RestController
@RequestMapping("/fpl")
@RequiredArgsConstructor
public class FplDebugController {

    private final FplClient fplClient;
    private final FplLookupService lookupService;
    private final FplLookupService fplLookupService;

    @GetMapping("/bootstrap")
    public Mono<JsonNode> bootstrap() {
        return fplClient.getBootstrapStatic();
    }

    @GetMapping("/fixtures")
    public Mono<JsonNode> fixtures() {
        return fplClient.getFixtures();
    }

    @GetMapping("/player/{id}")
    public Mono<Map<Integer, String>> player(@PathVariable int id) {
        return fplLookupService.getPlayerIdToNameMap();
    }

    @GetMapping("/players/enriched")
    public List<EnrichedPlayerDto> playersEnriched() {

        JsonNode bootstrap = fplClient.getBootstrapStatic().block();

        JsonNode teamsNode = bootstrap.get("teams");
        JsonNode positionsNode = bootstrap.get("element_types");
        JsonNode playersNode = bootstrap.get("elements");

        // Build teamId -> teamName map
        Map<Integer, String> teamMap = new HashMap<>();
        for (int i = 0; i < teamsNode.size(); i++) {
            JsonNode t = teamsNode.get(i);
            teamMap.put(
                    t.get("id").asInt(),
                    t.get("name").asText()
            );
        }

        // Build positionId -> positionName map
        Map<Integer, String> posMap = new HashMap<>();
        for (int i = 0; i < positionsNode.size(); i++) {
            JsonNode p = positionsNode.get(i);
            posMap.put(
                    p.get("id").asInt(),
                    p.get("singular_name").asText()
            );
        }

        // Build enriched players list
        List<EnrichedPlayerDto> result = new ArrayList<>();
        for (int i = 0; i < playersNode.size(); i++) {
            JsonNode p = playersNode.get(i);

            int playerId = p.get("id").asInt();
            int teamId = p.get("team").asInt();
            int positionId = p.get("element_type").asInt();

            result.add(new EnrichedPlayerDto(
                    playerId,
                    p.get("web_name").asText(),
                    p.get("first_name").asText(),
                    p.get("second_name").asText(),
                    teamId,
                    teamMap.getOrDefault(teamId, "UNKNOWN"),
                    positionId,
                    posMap.getOrDefault(positionId, "UNKNOWN"),
                    p.has("now_cost") ? p.get("now_cost").asInt() : null,
                    p.get("status").asText()
            ));
        }

        return result;
    }

    @GetMapping("/fixtures/enriched")
    public List<EnrichedFixtureDto> fixturesEnriched() {
        JsonNode fixtures = fplClient.getFixtures().block();
        Map<Integer, String> teamMap = lookupService.getTeamIdToNameMap().block();

        List<EnrichedFixtureDto> out = new ArrayList<>();

        for (int i = 0; i < fixtures.size(); i++) {
            JsonNode f = fixtures.get(i);

            int fixtureId = f.get("id").asInt();
            Integer gw = f.get("event").isNull() ? null : f.get("event").asInt();

            String kickoffStr = f.get("kickoff_time").isNull() ? null : f.get("kickoff_time").asText();
            OffsetDateTime kickoff = kickoffStr == null ? null : OffsetDateTime.parse(kickoffStr);

            int homeId = f.get("team_h").asInt();
            int awayId = f.get("team_a").asInt();

            out.add(new EnrichedFixtureDto(
                    fixtureId, gw, kickoff,
                    homeId, teamMap.getOrDefault(homeId, "UNKNOWN"),
                    awayId, teamMap.getOrDefault(awayId, "UNKNOWN"),
                    f.get("finished").asBoolean(false),
                    f.get("team_h_score").isNull() ? null : f.get("team_h_score").asInt(),
                    f.get("team_a_score").isNull() ? null : f.get("team_a_score").isNull() ? null : f.get("team_a_score").asInt()
            ));
        }

        return out;
    }
}
