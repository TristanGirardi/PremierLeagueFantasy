package com.example.PL.dto;

public record HealthResponse(
        String status,
        boolean modelLoaded,
        String modelVersion
) {}
