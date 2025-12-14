package com.example.PL.dto;

public record PlayerMatchStatsDto(
        int playerId,
        String playerName,
        int fixtureId,
        Integer gameweek,
        int minutesPlayed,
        String position, // Goalkeeper/Defender/Midfielder/Forward

        int goalsScored,
        int goalAssists,
        int cleanSheets,
        int shotSaves,

        // Free FPL provides this combined as one field:
        Integer clearancesBlockedInterceptions, // CBI

        // Not available separately in free FPL endpoints:
        Integer clearances,
        Integer blockedShots,
        Integer interceptions,
        Integer tackles,
        Integer recoveries,

        int penaltySaves,
        int penaltyMisses,
        int bonusPoints,

        int goalsConceded,
        int yellowCards,
        int redCards,
        int ownGoals
){
}
