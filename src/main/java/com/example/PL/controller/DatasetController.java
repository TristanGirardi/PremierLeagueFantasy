package com.example.PL.controller;

import com.example.PL.dto.PlayerFeatureRowDto;
import com.example.PL.service.DatasetService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/dataset")
public class DatasetController {

    private final DatasetService datasetService;

    public DatasetController(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    @GetMapping(value = "/train.csv", produces = "text/csv")
    public Flux<String> trainCsv() {
        return Flux.concat(
                Flux.just(datasetService.trainingCsvHeader()),
                datasetService.trainingRowsAllPlayers().map(datasetService::toCsvLine)
        );
    }
}
