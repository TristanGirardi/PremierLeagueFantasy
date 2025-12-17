package com.example.PL.service;

import com.example.PL.dto.PlayerFeatureRowDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class MLPredictionService {

    private final WebClient mlWebClient;
    private final DatasetService datasetService;

    public MLPredictionService(
            @Value("${fastapi.base-url:http://localhost:8001}") String fastapiBaseUrl,
            DatasetService datasetService
    ) {
        this.mlWebClient = WebClient.builder()
                .baseUrl(fastapiBaseUrl)
                .build();
        this.datasetService = datasetService;
    }

    // ===== TRAINING ENDPOINTS =====

    /**
     * Train the ML model with all available data
     * This fetches training data from DatasetService and sends it to FastAPI
     */
    public Mono<TrainResponse> trainModelWithAllData() {
        System.out.println("🔄 Starting model training...");

        return datasetService.trainingRowsAllPlayers()
                .collectList()
                .flatMap(rows -> {
                    System.out.println("📊 Collected " + rows.size() + " training rows");

                    TrainRequest request = new TrainRequest(rows);

                    return mlWebClient.post()
                            .uri("/train")
                            .bodyValue(request)
                            .retrieve()
                            .bodyToMono(TrainResponse.class)
                            .doOnSuccess(response ->
                                    System.out.println("✅ Training complete: " + response.message())
                            )
                            .doOnError(error ->
                                    System.err.println("❌ Training failed: " + error.getMessage())
                            );
                });
    }

    /**
     * Check if model is trained and ready
     */
    public Mono<HealthResponse> checkModelHealth() {
        return mlWebClient.get()
                .uri("/health")
                .retrieve()
                .bodyToMono(HealthResponse.class);
    }

    // ===== PREDICTION ENDPOINTS =====

    /**
     * Predict points for a single player's next fixture
     */
    public Mono<PredictionResponse> predictNextFixture(int playerId) {
        return datasetService.nextFixtureRowForPlayer(playerId)
                .flatMap(feature -> {
                    PredictRequest request = new PredictRequest(List.of(feature));

                    return mlWebClient.post()
                            .uri("/predict")
                            .bodyValue(request)
                            .retrieve()
                            .bodyToMono(PredictionResponse[].class)
                            .map(predictions -> predictions[0]); // Return first (only) prediction
                });
    }

    /**
     * Predict points for multiple players
     */
    public Flux<PredictionResponse> predictMultiplePlayers(List<Integer> playerIds) {
        return Flux.fromIterable(playerIds)
                .flatMap(playerId -> datasetService.nextFixtureRowForPlayer(playerId))
                .collectList()
                .flatMapMany(features -> {
                    if (features.isEmpty()) {
                        return Flux.empty();
                    }

                    PredictRequest request = new PredictRequest(features);

                    return mlWebClient.post()
                            .uri("/predict")
                            .bodyValue(request)
                            .retrieve()
                            .bodyToMono(PredictionResponse[].class)
                            .flatMapMany(Flux::fromArray);
                });
    }

    /**
     * Get top N predicted players for the next gameweek
     */
    public Flux<PredictionResponse> getTopPredictions(int limit) {
        return datasetService.trainingRowsAllPlayers()
                .map(PlayerFeatureRowDto::playerId)
                .distinct()
                .collectList()
                .flatMapMany(playerIds -> predictMultiplePlayers(playerIds))
                .sort((a, b) -> Double.compare(b.predictedPoints(), a.predictedPoints()))
                .take(limit);
    }

    // ===== REQUEST/RESPONSE MODELS =====

    public record TrainRequest(List<PlayerFeatureRowDto> rows) {}

    public record TrainResponse(
            String status,
            String message,
            int rowsProcessed,
            String modelVersion,
            String trainedAt
    ) {}

    public record PredictRequest(List<PlayerFeatureRowDto> rows) {}

    public record PredictionResponse(
            int playerId,
            Integer gameweek,
            int fixtureId,
            double predictedPoints
    ) {}

    public record HealthResponse(
            String status,
            boolean modelLoaded,
            String modelVersion
    ) {}
}
