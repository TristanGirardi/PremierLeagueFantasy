package com.example.PL.repository;

import com.example.PL.model.PlayerPrediction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerPredictionRepository extends JpaRepository<PlayerPrediction, Long> {

    /**
     * Find prediction for a specific player in a specific gameweek
     */
    Optional<PlayerPrediction> findByPlayerIdAndGameweek(Integer playerId, Integer gameweek);

    /**
     * Get all predictions for a gameweek
     */
    List<PlayerPrediction> findByGameweek(Integer gameweek);

    /**
     * Get top N predictions for a gameweek, ordered by predicted points
     */
    @Query("SELECT p FROM PlayerPrediction p WHERE p.gameweek = :gameweek ORDER BY p.predictedPoints DESC")
    List<PlayerPrediction> findTopByGameweekOrderByPredictedPointsDesc(Integer gameweek, Pageable pageable);

    /**
     * Delete all predictions for a gameweek (useful for regenerating)
     */
    void deleteByGameweek(Integer gameweek);

    /**
     * Check if predictions exist for a gameweek
     */
    boolean existsByGameweek(Integer gameweek);
}
