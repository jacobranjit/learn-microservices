package com.mc.oletrex.masterdata.controller;

import com.mc.oletrex.masterdata.client.ServiceUnavailableException;
import com.mc.oletrex.masterdata.csv.InvalidMasterDataException;
import com.mc.oletrex.masterdata.service.PopulationInProgressException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Turns exceptions into RFC 9457 "problem detail" JSON responses. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(PopulationInProgressException.class)
    public ProblemDetail handleInProgress(PopulationInProgressException ex) {
        return problem(HttpStatus.CONFLICT, "Population In Progress", ex.getMessage());
    }

    @ExceptionHandler(InvalidMasterDataException.class)
    public ProblemDetail handleInvalidMasterData(InvalidMasterDataException ex) {
        log.error("Invalid master data: {}", ex.getMessage());
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid Master Data", ex.getMessage());
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ProblemDetail handleServiceUnavailable(ServiceUnavailableException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "An unexpected error occurred.");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
