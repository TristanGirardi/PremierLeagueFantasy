package com.example.PL.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "player_predictions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"player_id", "gameweek"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player_id", nullable = false)
    private Integer playerId;

    @Column(nullable = false)
    private Integer gameweek;

    @Column(name = "fixture_id", nullable = false)
    private Integer fixtureId;

    @Column(name = "predicted_points", nullable = false)
    private Double predictedPoints;

    @Column(name = "prediction_date", nullable = false)
    private LocalDateTime predictionDate;

    @Column(name = "model_version")
    private String modelVersion;

    @Column(name = "actual_points")
    private Integer actualPoints;  // To track accuracy later

    public PlayerPrediction(Integer playerId, Integer gameweek, Integer fixtureId,
                            Double predictedPoints, String modelVersion) {
        this.playerId = playerId;
        this.gameweek = gameweek;
        this.fixtureId = fixtureId;
        this.predictedPoints = predictedPoints;
        this.modelVersion = modelVersion;
        this.predictionDate = LocalDateTime.now();
    }
}
