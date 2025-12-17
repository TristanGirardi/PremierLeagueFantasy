package com.example.PL.service;

import com.example.PL.dto.PlayerFeatureRowDto;
import com.example.PL.dto.HealthResponse;
import com.example.PL.dto.PredictionResponse;
import com.example.PL.dto.TrainResponse;
import com.example.PL.model.PlayerPrediction;
import com.example.PL.repository.PlayerPredictionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class PredictionService {

    private final DatasetService datasetService;
    private final MLServiceClient mlServiceClient;
    private final PlayerPredictionRepository predictionRepository;
    private final FplLookupService lookupService;

    public PredictionService(DatasetService datasetService,
                             MLServiceClient mlServiceClient,
                             PlayerPredictionRepository predictionRepository,
                             FplLookupService lookupService) {
        this.datasetService = datasetService;
        this.mlServiceClient = mlServiceClient;
        this.predictionRepository = predictionRepository;
        this.lookupService = lookupService;
    }

    /**
     * Check if ML service is available
     */
    public Mono<HealthResponse> checkMLServiceHealth() {
        return mlServiceClient.checkHealth();
    }

    /**
     * Train the ML model with current FPL data
     * This should be run periodically (e.g., weekly after gameweek finishes)
     */
    public Mono<TrainResponse> trainModel() {
        System.out.println("Starting model training...");

        return datasetService.trainingRowsAllPlayers()
                .collectList()
                .flatMap(trainingRows -> {
                    System.out.println("Collected " + trainingRows.size() + " training rows");

                    if (trainingRows.isEmpty()) {
                        return Mono.error(new RuntimeException("No training data available"));
                    }

                    return mlServiceClient.trainModel(trainingRows);
                })
                .doOnSuccess(response ->
                        System.out.println("Model trained successfully: " + response.modelVersion())
                )
                .doOnError(error ->
                        System.err.println("Model training failed: " + error.getMessage())
                );
    }

    /**
     * Generate predictions for the next gameweek and save to database
     */
    @Transactional
    public Mono<List<PlayerPrediction>> generateAndSavePredictions(Integer gameweek) {
        System.out.println("Generating predictions for gameweek " + gameweek);

        // First, check if ML service is ready
        return mlServiceClient.checkHealth()
                .flatMap(health -> {
                    if (!health.modelLoaded()) {
                        return Mono.error(new RuntimeException(
                                "ML service has no trained model. Please train the model first."
                        ));
                    }

                    // Get all player IDs
                    return lookupService.getAllPlayerIds()
                            .flatMapMany(Flux::fromIterable)
                            .flatMap(playerId ->
                                    // Get next fixture data for each player
                                    datasetService.nextFixtureRowForPlayer(playerId)
                                            .onErrorResume(e -> {
                                                System.err.println("Failed to get data for player " + playerId + ": " + e.getMessage());
                                                return Mono.empty(); // Skip this player
                                            })
                            )
                            .collectList()
                            .flatMap(featureRows -> {
                                System.out.println("Collected feature data for " + featureRows.size() + " players");

                                if (featureRows.isEmpty()) {
                                    return Mono.error(new RuntimeException("No player data available for prediction"));
                                }

                                // Get predictions from ML service
                                return mlServiceClient.predict(featureRows)
                                        .collectList()
                                        .map(predictions -> {
                                            // Save predictions to database
                                            List<PlayerPrediction> entities = predictions.stream()
                                                    .map(pred -> new PlayerPrediction(
                                                            pred.playerId(),
                                                            pred.gameweek(),
                                                            pred.fixtureId(),
                                                            pred.predictedPoints(),
                                                            health.modelVersion()
                                                    ))
                                                    .toList();

                                            // Delete old predictions for this gameweek if they exist
                                            if (predictionRepository.existsByGameweek(gameweek)) {
                                                predictionRepository.deleteByGameweek(gameweek);
                                            }

                                            // Save new predictions
                                            List<PlayerPrediction> saved = predictionRepository.saveAll(entities);
                                            System.out.println("Saved " + saved.size() + " predictions to database");

                                            return saved;
                                        });
                            });
                });
    }

    /**
     * Get predictions for a specific gameweek from database
     */
    public List<PlayerPrediction> getPredictionsForGameweek(Integer gameweek) {
        return predictionRepository.findByGameweek(gameweek);
    }

    /**
     * Get top N predicted players for a gameweek
     */
    public List<PlayerPrediction> getTopPredictions(Integer gameweek, int limit) {
        return predictionRepository.findTopByGameweekOrderByPredictedPointsDesc(
                gameweek,
                PageRequest.of(0, limit)
        );
    }

    /**
     * Get prediction for a specific player in a gameweek
     */
    public PlayerPrediction getPredictionForPlayer(Integer playerId, Integer gameweek) {
        return predictionRepository.findByPlayerIdAndGameweek(playerId, gameweek)
                .orElse(null);
    }

    /**
     * Check if predictions exist for a gameweek
     */
    public boolean predictionsExist(Integer gameweek) {
        return predictionRepository.existsByGameweek(gameweek);
    }
}