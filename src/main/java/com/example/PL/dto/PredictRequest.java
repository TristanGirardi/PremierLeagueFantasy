package com.example.PL.dto;

import java.util.List;

public record PredictRequest(List<PlayerFeatureRowDto> rows) {}
