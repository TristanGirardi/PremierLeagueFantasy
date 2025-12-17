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

    private static final int WINDOW = 5;
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
                    List<Integer> customPoints = computeCustomPoints(history, pos);

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

    /**
     * FIXED: Added proper null checking for players without upcoming fixtures
     */
    public Mono<PlayerFeatureRowDto> nextFixtureRowForPlayer(int playerId) {
        Mono<JsonNode> elementSummaryMono = fplClient.getElementSummary(playerId);
        Mono<Map<Integer, String>> playerNameMapMono = lookupService.getPlayerIdToWebNameMap();
        Mono<Map<Integer, Integer>> playerPosIdMapMono = lookupService.getPlayerIdToPositionIdMap();
        Mono<Map<Integer, String>> posNameMapMono = lookupService.getPositionIdToNameMap();

        return Mono.zip(elementSummaryMono, playerNameMapMono, playerPosIdMapMono, posNameMapMono)
                .flatMap(tuple -> {
                    JsonNode elementSummary = tuple.getT1();
                    Map<Integer, String> playerNameMap = tuple.getT2();
                    Map<Integer, Integer> posIdMap = tuple.getT3();
                    Map<Integer, String> posNameMap = tuple.getT4();

                    String playerName = playerNameMap.getOrDefault(playerId, "UNKNOWN");
                    int positionId = posIdMap.getOrDefault(playerId, 0);
                    String positionName = posNameMap.getOrDefault(positionId, "UNKNOWN");

                    List<FeatureBuilderService.MatchStats> history = featureBuilder.parseHistory(elementSummary);

                    // Check if player has played any matches
                    if (history.isEmpty()) {
                        return Mono.error(new IllegalStateException(
                                "Player " + playerId + " (" + playerName + ") has no match history"
                        ));
                    }

                    FantasyScoringService.Position pos = toPosition(positionId);
                    List<Integer> customPoints = computeCustomPoints(history, pos);
                    int effectiveWindow = windowEffective(history.size(), WINDOW);

                    var roll = featureBuilder.rollingFeatures(history, customPoints, effectiveWindow);

                    // Get upcoming fixtures
                    JsonNode fixtures = elementSummary.get("fixtures");

                    // ✅ FIX: Check if fixtures exist and have at least one upcoming match
                    if (fixtures == null || fixtures.size() == 0) {
                        return Mono.error(new IllegalStateException(
                                "No upcoming fixtures found for player " + playerId + " (" + playerName + ")"
                        ));
                    }

                    JsonNode next = fixtures.get(0);

                    // ✅ FIX: Safely extract fields with null checks
                    JsonNode idNode = next.get("id");
                    if (idNode == null) {
                        return Mono.error(new IllegalStateException(
                                "Fixture ID missing for player " + playerId
                        ));
                    }
                    int fixtureId = idNode.asInt();

                    JsonNode eventNode = next.get("event");
                    Integer gw = (eventNode == null || eventNode.isNull()) ? null : eventNode.asInt();

                    JsonNode isHomeNode = next.get("is_home");
                    boolean isHome = (isHomeNode != null) && isHomeNode.asBoolean(false);

//                    JsonNode opponentNode = next.get("opponent_team");
//                    if (opponentNode == null) {
//                        return Mono.error(new IllegalStateException(
//                                "Opponent team missing for fixture " + fixtureId
//                        ));
//                    }
                   // int opponentTeamId = opponentNode.asInt();

                    JsonNode difficultyNode = next.get("difficulty");
                    Integer fdr = (difficultyNode != null && !difficultyNode.isNull())
                            ? difficultyNode.asInt()
                            : null;

                    int teamId = -1;

                    return Mono.just(new PlayerFeatureRowDto(
                            playerId,
                            playerName,
                            positionId,
                            positionName,

                            fixtureId,
                            gw,
                            isHome,
                            0,
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
                    ));
                });
    }

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

        int label = customPoints.get(i);

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