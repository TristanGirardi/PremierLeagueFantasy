package com.example.PL.controller;

import com.example.PL.service.MLPredictionService;
import com.example.PL.service.MLPredictionService.*;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/ml")
public class MLController {

    private final MLPredictionService mlService;

    public MLController(MLPredictionService mlService) {
        this.mlService = mlService;
    }

    @PostMapping("/train")
    public Mono<TrainResponse> trainModel() {
        return mlService.trainModelWithAllData();
    }


    @GetMapping("/health")
    public Mono<HealthResponse> checkHealth() {
        return mlService.checkModelHealth();
    }

    @GetMapping("/predict/player/{playerId}")
    public Mono<PredictionResponse> predictPlayer(@PathVariable int playerId) {
        return mlService.predictNextFixture(playerId);
    }

    @PostMapping("/predict/batch")
    public Flux<PredictionResponse> predictBatch(@RequestBody BatchRequest request) {
        return mlService.predictMultiplePlayers(request.playerIds());
    }


    @GetMapping("/predict/top")
    public Flux<PredictionResponse> getTopPredictions(
            @RequestParam(defaultValue = "10") int limit
    ) {
        return mlService.getTopPredictions(limit);
    }


    @GetMapping("/predict/compare")
    public Mono<CompareResponse> comparePlayers(
            @RequestParam int player1,
            @RequestParam int player2
    ) {
        return Mono.zip(
                mlService.predictNextFixture(player1),
                mlService.predictNextFixture(player2)
        ).map(tuple -> new CompareResponse(
                tuple.getT1(),
                tuple.getT2(),
                tuple.getT1().predictedPoints() - tuple.getT2().predictedPoints()
        ));
    }

    public record BatchRequest(List<Integer> playerIds) {}

    public record CompareResponse(
            PredictionResponse player1,
            PredictionResponse player2,
            double pointsDifference
    ) {}
}