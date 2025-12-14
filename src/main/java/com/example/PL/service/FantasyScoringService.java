package com.example.PL.service;

import org.springframework.stereotype.Service;

@Service
public class FantasyScoringService {

    public enum Position {
        GK, DEF, MID, FWD
    }

    public int scoreMatch(FeatureBuilderService.MatchStats m, Position pos) {
        int points = 0;

        // Minutes
        if (m.minutes() > 0 && m.minutes() < 60) points += 1;
        else if (m.minutes() >= 60) points += 2;

        // Goals by position
        points += switch (pos) {
            case GK -> 10 * m.goals();
            case DEF -> 6 * m.goals();
            case MID -> 5 * m.goals();
            case FWD -> 4 * m.goals();
        };

        // Assists
        points += 3 * m.assists();

        // Clean sheet
        if (m.cleanSheets() > 0) {
            if (pos == Position.GK || pos == Position.DEF) points += 4;
            else if (pos == Position.MID) points += 1;
        }

        // Saves: 1 per 3 saves (GK only)
        if (pos == Position.GK) {
            points += (m.saves() / 3);
        }


        if (pos == Position.DEF) {
            if (m.cbi() >= 10) points += 2;
        } else if (pos == Position.MID || pos == Position.FWD) {
            if (m.cbi() >= 12) points += 2;
        }

        // Penalties
        points += 5 * m.penaltiesSaved();
        points += -2 * m.penaltiesMissed();

        // Bonus
        points += m.bonus();

        // Goals conceded: -1 per 2 conceded (GK/DEF)
        if (pos == Position.GK || pos == Position.DEF) {
            points += -(m.goalsConceded() / 2);
        }

        // Cards / own goals
        points += -1 * m.yellow();
        points += -3 * m.red();
        points += -2 * m.ownGoals();

        return points;
    }
}
