package com.example.PL.dto;

public record PlayerFeatureRowDto(
        int playerId,
        String playerName,
        int positionId,
        String positionName,

        int fixtureId,
        Integer gameweek,
        boolean isHome,
        int opponentTeamId,
        int teamId,
        Integer fixtureDifficulty,

        // Rolling features (window=5)
        double avgMinutes5,
        double avgPoints5,
        double avgGoals5,
        double avgAssists5,
        double avgSaves5,
        double avgCleanSheets5,
        double avgBonus5,
        double avgGoalsConceded5,
        double avgCbi5,
        double avgYellow5,
        double avgRed5,

        // Label (training only). null for prediction rows.
        Integer labelTotalPoints
) {
}
