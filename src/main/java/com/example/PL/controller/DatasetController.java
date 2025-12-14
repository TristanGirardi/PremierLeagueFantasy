package com.example.PL.controller;

import com.example.PL.dto.PlayerFeatureRowDto;
import com.example.PL.service.DatasetService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/dataset")
public class DatasetController {

    private final DatasetService datasetService;

    public DatasetController(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    // Training rows for one player (label included)
    @GetMapping("/player/{playerId}/train")
    public Flux<PlayerFeatureRowDto> train(@PathVariable int playerId) {
        return datasetService.trainingRowsForPlayer(playerId);
    }

    // Next fixture row for one player (label null)
    @GetMapping("/player/{playerId}/next")
    public Mono<PlayerFeatureRowDto> next(@PathVariable int playerId) {
        return datasetService.nextFixtureRowForPlayer(playerId);
    }

    // 381
    @GetMapping(value = "/player/{playerId}/train.csv", produces = "text/csv")
    public Mono<String> trainCsv(@PathVariable int playerId) {
        return datasetService.trainingRowsForPlayer(playerId)
                .collectList()
                .map(rows -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("playerId,positionId,fixtureId,gameweek,isHome,opponentTeamId,fixtureDifficulty,")
                            .append("avgMinutes5,avgPoints5,avgGoals5,avgAssists5,avgSaves5,avgCleanSheets5,avgBonus5,avgGoalsConceded5,avgCbi5,avgYellow5,avgRed5,labelTotalPoints\n");

                    for (PlayerFeatureRowDto r : rows) {
                        sb.append(r.playerId()).append(",")
                                .append(r.positionId()).append(",")
                                .append(r.fixtureId()).append(",")
                                .append(r.gameweek()).append(",")
                                .append(r.isHome()).append(",")
                                .append(r.opponentTeamId()).append(",")
                                .append(r.fixtureDifficulty()).append(",")
                                .append(r.avgMinutes5()).append(",")
                                .append(r.avgGoals5()).append(",")
                                .append(r.avgAssists5()).append(",")
                                .append(r.avgSaves5()).append(",")
                                .append(r.avgCleanSheets5()).append(",")
                                .append(r.avgBonus5()).append(",")
                                .append(r.avgGoalsConceded5()).append(",")
                                .append(r.avgCbi5()).append(",")
                                .append(r.avgYellow5()).append(",")
                                .append(r.avgRed5()).append(",")
                                .append(r.labelTotalPoints()).append("\n");
                    }
                    return sb.toString();
                });
    }

    @GetMapping("/train")
    public Flux<PlayerFeatureRowDto> trainJson() {
        return datasetService.trainingRowsAllPlayers();
    }

    // Model-ready: download/stream CSV
    @GetMapping(value = "/train.csv", produces = "text/csv")
    public Flux<String> trainCsv() {
        return Flux.concat(
                Flux.just(datasetService.trainingCsvHeader()),
                datasetService.trainingRowsAllPlayers().map(datasetService::toCsvLine)
        );
    }
}
