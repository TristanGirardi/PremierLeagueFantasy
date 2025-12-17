package com.example.PL.config;

import com.example.PL.service.PredictionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled tasks for automatic model training and prediction generation
 */
@Component
public class PredictionScheduler {

    private final PredictionService predictionService;

    public PredictionScheduler(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    /**
     * Train model every Monday at 2 AM
     * (After weekend fixtures are complete)
     * Cron format: second minute hour day month day-of-week
     */
    @Scheduled(cron = "0 0 2 * * MON")
    public void scheduledModelTraining() {
        System.out.println("=== SCHEDULED: Starting model training ===");

        predictionService.trainModel()
                .subscribe(
                        response -> System.out.println("✓ Model trained: " + response.modelVersion()),
                        error -> System.err.println("✗ Training failed: " + error.getMessage())
                );
    }

    /**
     * Generate predictions every day at 3 AM
     * (After training completes, and before gameweek starts)
     *
     * NOTE: You'll need to determine the current/next gameweek dynamically
     * For now, this is a placeholder
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void scheduledPredictionGeneration() {
        System.out.println("=== SCHEDULED: Generating predictions ===");

        // TODO: Get the next gameweek number from FPL API
        Integer nextGameweek = getCurrentOrNextGameweek();

        if (nextGameweek != null) {
            predictionService.generateAndSavePredictions(nextGameweek)
                    .subscribe(
                            predictions -> System.out.println("✓ Generated " + predictions.size() + " predictions for GW" + nextGameweek),
                            error -> System.err.println("✗ Prediction generation failed: " + error.getMessage())
                    );
        } else {
            System.out.println("No upcoming gameweek found");
        }
    }

    /**
     * Helper method to get current or next gameweek
     * TODO: Implement this by checking FPL fixtures API
     */
    private Integer getCurrentOrNextGameweek() {
        // Placeholder - you would query your FPL fixtures to find:
        // 1. Current gameweek if in progress
        // 2. Next gameweek if between gameweeks
        // For now, return null to disable auto-scheduling
        return null;
    }

    /**
     * Manual trigger for testing (can be called via endpoint)
     */
    public void manualTrainAndPredict(Integer gameweek) {
        System.out.println("=== MANUAL: Train and predict for GW" + gameweek + " ===");

        predictionService.trainModel()
                .flatMap(trainResponse -> {
                    System.out.println("✓ Training complete");
                    return predictionService.generateAndSavePredictions(gameweek);
                })
                .subscribe(
                        predictions -> System.out.println("✓ Complete: " + predictions.size() + " predictions"),
                        error -> System.err.println("✗ Failed: " + error.getMessage())
                );
    }
}
