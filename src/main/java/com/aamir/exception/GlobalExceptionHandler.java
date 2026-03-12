package com.aamir.exception;

// GlobalExceptionHandler.java
// Problem solved: scroll token corruption, invalid enum values, bad date formats
// should return 400, not 500.


import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles bad enum values (e.g., status=UNKNOWN) and type mismatches
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Invalid Request Parameter");
        problem.setDetail("Parameter '" + ex.getName() + "' has invalid value: " + ex.getValue());
        problem.setType(URI.create("/errors/invalid-parameter"));
        return problem;
    }

    /**
     * Catch-all — prevents stack traces leaking to clients
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneral(Exception ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setTitle("Internal Server Error");
        problem.setDetail("An unexpected error occurred. Please try again.");
        return problem;
    }
}
