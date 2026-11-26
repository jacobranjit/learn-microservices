package com.mc.oletrex.masterdata.controller;

import com.mc.oletrex.masterdata.dto.MasterDataSummary;
import com.mc.oletrex.masterdata.dto.PopulateReport;
import com.mc.oletrex.masterdata.service.MasterDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Master Data", description = "Load initial test data from CSV files into the other services")
@RequestMapping("/api/master-data")
@RequiredArgsConstructor
public class MasterDataController {

    private final MasterDataService masterDataService;

    @Operation(summary = "Show what populate would load (row counts in the CSV files)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "CSV files read"),
                    @ApiResponse(responseCode = "500", description = "A CSV file is missing or malformed", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
            })
    @GetMapping
    public MasterDataSummary summary() {
        return masterDataService.summary();
    }

    @Operation(summary = "Populate locations, products and stock from the CSV files",
            description = "Calls the inventory and product APIs: locations first, then products, then stock. "
                    + "Records that already exist are reset to the CSV values, so it is safe to run again. "
                    + "Product and inventory services must be running.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Finished; see success, the per-step counts and errors"),
                    @ApiResponse(responseCode = "409", description = "A populate run is already in progress", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
                    @ApiResponse(responseCode = "500", description = "A CSV file is missing or malformed; nothing was sent", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
                    @ApiResponse(responseCode = "503", description = "Product or inventory service unreachable; nothing was sent", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
            })
    @PostMapping("/populate")
    public PopulateReport populate() {
        return masterDataService.populate();
    }
}
