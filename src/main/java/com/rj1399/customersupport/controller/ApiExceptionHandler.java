package com.rj1399.customersupport.controller;

import com.rj1399.customersupport.api.ApiDtos;
import com.rj1399.customersupport.service.CustomerSupportService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.time.Instant;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(CustomerSupportService.ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiDtos.ErrorResponse handleNotFound(CustomerSupportService.ResourceNotFoundException ex) {
        return new ApiDtos.ErrorResponse("NOT_FOUND", ex.getMessage(), Instant.now());
    }

    @ExceptionHandler(CustomerSupportService.BusinessRuleException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiDtos.ErrorResponse handleBusinessRule(CustomerSupportService.BusinessRuleException ex) {
        return new ApiDtos.ErrorResponse(ex.getCode(), ex.getMessage(), Instant.now());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiDtos.ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("Request validation failed");
        return new ApiDtos.ErrorResponse("VALIDATION_ERROR", message, Instant.now());
    }
}
