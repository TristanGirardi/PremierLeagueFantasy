package com.example.PL.dto;

import java.time.OffsetDateTime;

public record EnrichedFixtureDto (
        int fixtureId,
        Integer gameweek,
        OffsetDateTime kickoffTime,
        int homeTeamId,
        String homeTeamName,
        int awayTeamId,
        String awayTeamName,
        Boolean finished,
        Integer homeScore,
        Integer awayScore
) {
}
