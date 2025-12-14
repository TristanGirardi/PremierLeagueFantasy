package com.example.PL.service;

import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class FeatureBuilderService {

    public record MatchStats(
            int fixtureId,
            Integer round,
            int minutes,
            int goals,
            int assists,
            int cleanSheets,
            int saves,
            int bonus,
            int goalsConceded,
            int cbi,
            int yellow,
            int red,
            int ownGoals,
            int penaltiesSaved,
            int penaltiesMissed,
            int opponentTeamId,
            boolean wasHome
    ) {}

    public List<MatchStats> parseHistory(JsonNode elementSummary) {
        JsonNode history = elementSummary.get("history");
        List<MatchStats> out = new ArrayList<>(history.size());

        for (int i = 0; i < history.size(); i++) {
            JsonNode h = history.get(i);

            out.add(new MatchStats(
                    h.get("fixture").asInt(),
                    h.get("round").isNull() ? null : h.get("round").asInt(),
                    h.get("minutes").asInt(),
                    h.get("goals_scored").asInt(),
                    h.get("assists").asInt(),
                    h.get("clean_sheets").asInt(),
                    h.get("saves").asInt(),
                    h.get("bonus").asInt(),
                    h.get("goals_conceded").asInt(),
                    h.get("clearances_blocks_interceptions").asInt(),
                    h.get("yellow_cards").asInt(),
                    h.get("red_cards").asInt(),
                    h.get("own_goals").asInt(),
                    h.get("penalties_saved").asInt(),
                    h.get("penalties_missed").asInt(),
                    h.get("opponent_team").asInt(),
                    h.get("was_home").asBoolean(false)
            ));
        }

        return out;
    }

    public static double avgInt(List<Integer> values) {
        if (values.isEmpty()) return 0.0;
        long sum = 0;
        for (int v : values) sum += v;
        return (double) sum / values.size();
    }


    public RollingFeatures rollingFeatures(
            List<MatchStats> priorMatches,
            List<Integer> priorCustomPoints,
            int window
    ) {
        int n = priorMatches.size();
        int start = Math.max(0, n - window);

        List<MatchStats> slice = priorMatches.subList(start, n);
        List<Integer> pointsSlice = priorCustomPoints.subList(start, n);

        List<Integer> minutes = new ArrayList<>();
        List<Integer> goals = new ArrayList<>();
        List<Integer> assists = new ArrayList<>();
        List<Integer> saves = new ArrayList<>();
        List<Integer> cs = new ArrayList<>();
        List<Integer> bonus = new ArrayList<>();
        List<Integer> gc = new ArrayList<>();
        List<Integer> cbi = new ArrayList<>();
        List<Integer> yellow = new ArrayList<>();
        List<Integer> red = new ArrayList<>();

        for (MatchStats m : slice) {
            minutes.add(m.minutes());
            goals.add(m.goals());
            assists.add(m.assists());
            saves.add(m.saves());
            cs.add(m.cleanSheets());
            bonus.add(m.bonus());
            gc.add(m.goalsConceded());
            cbi.add(m.cbi());
            yellow.add(m.yellow());
            red.add(m.red());
        }

        return new RollingFeatures(
                avgInt(minutes),
                avgInt(pointsSlice),
                avgInt(goals),
                avgInt(assists),
                avgInt(saves),
                avgInt(cs),
                avgInt(bonus),
                avgInt(gc),
                avgInt(cbi),
                avgInt(yellow),
                avgInt(red)
        );
    }

    public record RollingFeatures(
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
            double avgRed5
    ) {}
}
