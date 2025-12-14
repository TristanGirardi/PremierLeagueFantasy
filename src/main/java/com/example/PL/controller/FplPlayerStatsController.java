package com.example.PL.controller;

import com.example.PL.dto.PlayerMatchStatsDto;
import com.example.PL.service.FplClient;
import com.example.PL.service.FplLookupService;
import tools.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/fpl")
public class FplPlayerStatsController {

    private final FplClient fplClient;
    private final FplLookupService lookupService;

    public FplPlayerStatsController(FplClient fplClient, FplLookupService lookupService) {
        this.fplClient = fplClient;
        this.lookupService = lookupService;
    }

    // 381
    @GetMapping("/player/{playerId}/matches")
    public Flux<PlayerMatchStatsDto> playerMatchStats(@PathVariable int playerId) {

        Mono<JsonNode> elementSummaryMono = fplClient.getElementSummary(playerId);
        Mono<Map<Integer, String>> playerNameMapMono = lookupService.getPlayerIdToWebNameMap();
        Mono<Map<Integer, Integer>> playerPosIdMapMono = lookupService.getPlayerIdToPositionIdMap();
        Mono<Map<Integer, String>> posNameMapMono = lookupService.getPositionIdToNameMap();

        return Mono.zip(elementSummaryMono, playerNameMapMono, playerPosIdMapMono, posNameMapMono)
                .flatMapMany(tuple -> {
                    JsonNode elementSummary = tuple.getT1();
                    Map<Integer, String> playerNameMap = tuple.getT2();
                    Map<Integer, Integer> playerPosIdMap = tuple.getT3();
                    Map<Integer, String> posNameMap = tuple.getT4();

                    String playerName = playerNameMap.getOrDefault(playerId, "UNKNOWN");
                    int posId = playerPosIdMap.getOrDefault(playerId, 0);
                    String posName = posNameMap.getOrDefault(posId, "UNKNOWN");

                    JsonNode history = elementSummary.get("history"); // array

                    return Flux.range(0, history.size())
                            .map(i -> history.get(i))
                            .map(h -> {
                                int fixtureId = h.get("fixture").asInt();
                                Integer gw = h.get("round").isNull() ? null : h.get("round").asInt();

                                int minutes = h.get("minutes").asInt();
                                int goals = h.get("goals_scored").asInt();
                                int assists = h.get("assists").asInt();
                                int cleanSheets = h.get("clean_sheets").asInt();
                                int saves = h.get("saves").asInt();

                                int cbi = h.get("clearances_blocks_interceptions").asInt();

                                int penSaved = h.get("penalties_saved").asInt();
                                int penMissed = h.get("penalties_missed").asInt();
                                int bonus = h.get("bonus").asInt();

                                int goalsConceded = h.get("goals_conceded").asInt();
                                int yellow = h.get("yellow_cards").asInt();
                                int red = h.get("red_cards").asInt();
                                int ownGoals = h.get("own_goals").asInt();

                                return new PlayerMatchStatsDto(
                                        playerId,
                                        playerName,
                                        fixtureId,
                                        gw,
                                        minutes,
                                        posName,

                                        goals,
                                        assists,
                                        cleanSheets,
                                        saves,

                                        cbi,

                                        // Not available separately in free FPL:
                                        null, // clearances
                                        null, // blockedShots
                                        null, // interceptions
                                        null, // tackles
                                        null, // recoveries

                                        penSaved,
                                        penMissed,
                                        bonus,

                                        goalsConceded,
                                        yellow,
                                        red,
                                        ownGoals
                                );
                            });
                });
    }
}
