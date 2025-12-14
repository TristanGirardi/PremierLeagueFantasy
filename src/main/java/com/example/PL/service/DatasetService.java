package com.example.PL.service;

import com.example.PL.dto.PlayerFeatureRowDto;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DatasetService {

    private final FplClient fplClient;
    private final FplLookupService lookupService;
    private final FeatureBuilderService featureBuilder;
    private final FantasyScoringService scoringService;

    // Change these anytime
    private static final int WINDOW = 5;          // or 15 later
    private static final int CONCURRENCY = 6;

    public DatasetService(
            FplClient fplClient,
            FplLookupService lookupService,
            FeatureBuilderService featureBuilder,
            FantasyScoringService scoringService
    ) {
        this.fplClient = fplClient;
        this.lookupService = lookupService;
        this.featureBuilder = featureBuilder;
        this.scoringService = scoringService;
    }

    public String trainingCsvHeader() {
        return "playerId,positionId,fixtureId,gameweek,isHome,opponentTeamId," +
                "avgMinutes5,avgPoints5,avgGoals5,avgAssists5,avgSaves5,avgCleanSheets5,avgBonus5," +
                "avgGoalsConceded5,avgCbi5,avgYellow5,avgRed5,labelTotalPoints\n";
    }

    /** One big dataset: rows for ALL players (training rows with labelTotalPoints filled). */
    public Flux<PlayerFeatureRowDto> trainingRowsAllPlayers() {

        Mono<List<Integer>> playerIdsMono = lookupService.getAllPlayerIds();
        Mono<Map<Integer, String>> playerNameMapMono = lookupService.getPlayerIdToWebNameMap();
        Mono<Map<Integer, Integer>> playerPosIdMapMono = lookupService.getPlayerIdToPositionIdMap();
        Mono<Map<Integer, String>> posNameMapMono = lookupService.getPositionIdToNameMap();

        return Mono.zip(playerIdsMono, playerNameMapMono, playerPosIdMapMono, posNameMapMono)
                .flatMapMany(tuple -> {
                    List<Integer> playerIds = tuple.getT1();
                    Map<Integer, String> playerNameMap = tuple.getT2();
                    Map<Integer, Integer> posIdMap = tuple.getT3();
                    Map<Integer, String> posNameMap = tuple.getT4();

                    return Flux.fromIterable(playerIds)
                            .flatMap(playerId ->
                                            trainingRowsForOnePlayer(
                                                    playerId,
                                                    playerNameMap,
                                                    posIdMap,
                                                    posNameMap,
                                                    WINDOW
                                            ),
                                    CONCURRENCY
                            );
                });
    }

    /** Training rows for a single player (labelTotalPoints filled). Useful for debugging. */
    public Flux<PlayerFeatureRowDto> trainingRowsForPlayer(int playerId) {

        Mono<JsonNode> elementSummaryMono = fplClient.getElementSummary(playerId);
        Mono<Map<Integer, String>> playerNameMapMono = lookupService.getPlayerIdToWebNameMap();
        Mono<Map<Integer, Integer>> playerPosIdMapMono = lookupService.getPlayerIdToPositionIdMap();
        Mono<Map<Integer, String>> posNameMapMono = lookupService.getPositionIdToNameMap();

        return Mono.zip(elementSummaryMono, playerNameMapMono, playerPosIdMapMono, posNameMapMono)
                .flatMapMany(tuple -> {
                    JsonNode elementSummary = tuple.getT1();
                    Map<Integer, String> playerNameMap = tuple.getT2();
                    Map<Integer, Integer> posIdMap = tuple.getT3();
                    Map<Integer, String> posNameMap = tuple.getT4();

                    String playerName = playerNameMap.getOrDefault(playerId, "UNKNOWN");
                    int positionId = posIdMap.getOrDefault(playerId, 0);
                    String positionName = posNameMap.getOrDefault(positionId, "UNKNOWN");

                    List<FeatureBuilderService.MatchStats> history = featureBuilder.parseHistory(elementSummary);

                    if (history.size() <= 1) return Flux.empty();

                    FantasyScoringService.Position pos = toPosition(positionId);

                    // Precompute custom points for all matches
                    List<Integer> customPoints = computeCustomPoints(history, pos);

                    // Variable window so you get MANY rows (not 1 row/player)
                    return Flux.range(1, history.size() - 1)
                            .map(i -> buildTrainingRow(
                                    playerId,
                                    playerName,
                                    positionId,
                                    positionName,
                                    history,
                                    customPoints,
                                    i,
                                    windowEffective(i, WINDOW)
                            ));
                });
    }

    /** Prediction row for a player's NEXT fixture (labelTotalPoints = null). */
    public Mono<PlayerFeatureRowDto> nextFixtureRowForPlayer(int playerId) {

        Mono<JsonNode> elementSummaryMono = fplClient.getElementSummary(playerId);
        Mono<Map<Integer, String>> playerNameMapMono = lookupService.getPlayerIdToWebNameMap();
        Mono<Map<Integer, Integer>> playerPosIdMapMono = lookupService.getPlayerIdToPositionIdMap();
        Mono<Map<Integer, String>> posNameMapMono = lookupService.getPositionIdToNameMap();

        return Mono.zip(elementSummaryMono, playerNameMapMono, playerPosIdMapMono, posNameMapMono)
                .map(tuple -> {
                    JsonNode elementSummary = tuple.getT1();
                    Map<Integer, String> playerNameMap = tuple.getT2();
                    Map<Integer, Integer> posIdMap = tuple.getT3();
                    Map<Integer, String> posNameMap = tuple.getT4();

                    String playerName = playerNameMap.getOrDefault(playerId, "UNKNOWN");
                    int positionId = posIdMap.getOrDefault(playerId, 0);
                    String positionName = posNameMap.getOrDefault(positionId, "UNKNOWN");

                    List<FeatureBuilderService.MatchStats> history = featureBuilder.parseHistory(elementSummary);

                    FantasyScoringService.Position pos = toPosition(positionId);
                    List<Integer> customPoints = computeCustomPoints(history, pos);

                    int effectiveWindow = windowEffective(history.size(), WINDOW);

                    // Rolling features from full history
                    var roll = featureBuilder.rollingFeatures(history, customPoints, effectiveWindow);

                    // Next fixture (upcoming)
                    JsonNode fixtures = elementSummary.get("fixtures");
                    if (fixtures == null || fixtures.size() == 0) {
                        throw new IllegalStateException("No upcoming fixtures found for player " + playerId);
                    }

                    JsonNode next = fixtures.get(0);
                    int fixtureId = next.get("id").asInt();
                    Integer gw = next.get("event").isNull() ? null : next.get("event").asInt();

                    boolean isHome = next.get("is_home").asBoolean(false);
                    int opponentTeamId = next.get("opponent_team").asInt();

                    Integer fdr = next.has("difficulty") ? next.get("difficulty").asInt() : null;

                    int teamId = -1;

                    return new PlayerFeatureRowDto(
                            playerId,
                            playerName,
                            positionId,
                            positionName,

                            fixtureId,
                            gw,
                            isHome,
                            opponentTeamId,
                            teamId,
                            fdr,

                            roll.avgMinutes5(),
                            roll.avgPoints5(),
                            roll.avgGoals5(),
                            roll.avgAssists5(),
                            roll.avgSaves5(),
                            roll.avgCleanSheets5(),
                            roll.avgBonus5(),
                            roll.avgGoalsConceded5(),
                            roll.avgCbi5(),
                            roll.avgYellow5(),
                            roll.avgRed5(),

                            null
                    );
                });
    }

    /** Internal helper used by the all-players dataset builder. */
    private Flux<PlayerFeatureRowDto> trainingRowsForOnePlayer(
            int playerId,
            Map<Integer, String> playerNameMap,
            Map<Integer, Integer> posIdMap,
            Map<Integer, String> posNameMap,
            int window
    ) {
        String playerName = playerNameMap.getOrDefault(playerId, "UNKNOWN");
        int positionId = posIdMap.getOrDefault(playerId, 0);
        String positionName = posNameMap.getOrDefault(positionId, "UNKNOWN");

        FantasyScoringService.Position pos = toPosition(positionId);

        return fplClient.getElementSummary(playerId)
                .flatMapMany(summary -> {
                    List<FeatureBuilderService.MatchStats> history = featureBuilder.parseHistory(summary);
                    if (history.size() <= 1) return Flux.empty();

                    List<Integer> customPoints = computeCustomPoints(history, pos);

                    // Variable window: i starts at 1, window grows until it hits WINDOW
                    return Flux.range(1, history.size() - 1)
                            .map(i -> buildTrainingRow(
                                    playerId,
                                    playerName,
                                    positionId,
                                    positionName,
                                    history,
                                    customPoints,
                                    i,
                                    windowEffective(i, window)
                            ));
                });
    }

    /** Build one labeled training row at index i (features from [0..i-1], label from i). */
    private PlayerFeatureRowDto buildTrainingRow(
            int playerId,
            String playerName,
            int positionId,
            String positionName,
            List<FeatureBuilderService.MatchStats> history,
            List<Integer> customPoints,
            int i,
            int effectiveWindow
    ) {
        var current = history.get(i);

        var priorMatches = history.subList(0, i);
        var priorPoints = customPoints.subList(0, i);

        var roll = featureBuilder.rollingFeatures(priorMatches, priorPoints, effectiveWindow);

        int label = customPoints.get(i); // ✅ your scoring

        int teamId = -1;
        Integer fixtureDifficulty = null;

        return new PlayerFeatureRowDto(
                playerId,
                playerName,
                positionId,
                positionName,

                current.fixtureId(),
                current.round(),
                current.wasHome(),
                current.opponentTeamId(),
                teamId,
                fixtureDifficulty,

                roll.avgMinutes5(),
                roll.avgPoints5(),
                roll.avgGoals5(),
                roll.avgAssists5(),
                roll.avgSaves5(),
                roll.avgCleanSheets5(),
                roll.avgBonus5(),
                roll.avgGoalsConceded5(),
                roll.avgCbi5(),
                roll.avgYellow5(),
                roll.avgRed5(),

                label
        );
    }

    /** Convert a row to one CSV line */
    public String toCsvLine(PlayerFeatureRowDto r) {
        return r.playerId() + "," +
                r.positionId() + "," +
                r.fixtureId() + "," +
                (r.gameweek() == null ? "" : r.gameweek()) + "," +
                r.isHome() + "," +
                r.opponentTeamId() + "," +
                r.avgMinutes5() + "," +
                r.avgPoints5() + "," +
                r.avgGoals5() + "," +
                r.avgAssists5() + "," +
                r.avgSaves5() + "," +
                r.avgCleanSheets5() + "," +
                r.avgBonus5() + "," +
                r.avgGoalsConceded5() + "," +
                r.avgCbi5() + "," +
                r.avgYellow5() + "," +
                r.avgRed5() + "," +
                (r.labelTotalPoints() == null ? "" : r.labelTotalPoints()) + "\n";
    }

    private FantasyScoringService.Position toPosition(int posId) {
        return switch (posId) {
            case 1 -> FantasyScoringService.Position.GK;
            case 2 -> FantasyScoringService.Position.DEF;
            case 3 -> FantasyScoringService.Position.MID;
            case 4 -> FantasyScoringService.Position.FWD;
            default -> FantasyScoringService.Position.MID;
        };
    }

    private int windowEffective(int available, int window) {
        return Math.max(1, Math.min(window, available));
    }

    private List<Integer> computeCustomPoints(List<FeatureBuilderService.MatchStats> history,
                                              FantasyScoringService.Position pos) {
        List<Integer> pts = new ArrayList<>(history.size());
        for (var m : history) {
            pts.add(scoringService.scoreMatch(m, pos));
        }
        return pts;
    }
}
