package com.example.PL.service;

import com.example.PL.dto.*;
import com.example.PL.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Client to communicate with the Python ML microservice
 */
@Service
public class MLServiceClient {

    private final WebClient mlServiceWebClient;

    public MLServiceClient(WebClient mlServiceWebClient) {
        this.mlServiceWebClient = mlServiceWebClient;
    }

    /**
     * Check if ML service is healthy and if a model is loaded
     */
    public Mono<HealthResponse> checkHealth() {
        return mlServiceWebClient
                .get()
                .uri("/health")
                .retrieve()
                .bodyToMono(HealthResponse.class)
                .doOnError(e -> System.err.println("ML Service health check failed: " + e.getMessage()))
                .onErrorReturn(new HealthResponse("error", false, null));
    }

    /**
     * Train a new model with the provided training data
     */
    public Mono<TrainResponse> trainModel(List<PlayerFeatureRowDto> trainingRows) {
        TrainRequest request = new TrainRequest(trainingRows);

        return mlServiceWebClient
                .post()
                .uri("/train")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(TrainResponse.class)
                .doOnSubscribe(s -> System.out.println("Sending " + trainingRows.size() + " rows for training..."))
                .doOnSuccess(response -> System.out.println("Training completed: " + response.message()))
                .doOnError(e -> System.err.println("Training failed: " + e.getMessage()));
    }

    /**
     * Get predictions for the provided feature rows
     */
    public Flux<PredictionResponse> predict(List<PlayerFeatureRowDto> featureRows) {
        PredictRequest request = new PredictRequest(featureRows);

        return mlServiceWebClient
                .post()
                .uri("/predict")
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(PredictionResponse.class)
                .doOnSubscribe(s -> System.out.println("Requesting predictions for " + featureRows.size() + " players..."))
                .doOnComplete(() -> System.out.println("Predictions received"))
                .doOnError(e -> System.err.println("Prediction failed: " + e.getMessage()));
    }
}
