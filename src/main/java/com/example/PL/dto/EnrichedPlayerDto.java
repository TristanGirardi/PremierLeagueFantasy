package com.example.PL.dto;

public record EnrichedPlayerDto(
        int playerId,
        String webName,
        String firstName,
        String secondName,
        int teamId,
        String teamName,
        int positionId,
        String positionName,
        Integer nowCost,
        String status
){
}
