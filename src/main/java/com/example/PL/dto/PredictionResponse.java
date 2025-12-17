package com.example.PL.dto;

public record PredictionResponse(
        int playerId,
        Integer gameweek,
        int fixtureId,
        double predictedPoints
) {}
