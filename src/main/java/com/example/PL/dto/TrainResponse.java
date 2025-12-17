package com.example.PL.dto;

public record TrainResponse(
        String status,
        String message,
        int rowsProcessed,
        String modelVersion,
        String trainedAt
) {}
