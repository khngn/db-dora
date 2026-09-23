package kn.jdb.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleResponseStatusException(ResponseStatusException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason());
        Throwable cause = exception.getCause();
        if (cause != null) {
            problemDetail.setProperty("error", cause.getMessage());
            problemDetail.setProperty("stackTrace", stackTraceToList(cause));
        }
        return problemDetail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgumentException(IllegalArgumentException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problemDetail.setProperty("stackTrace", stackTraceToList(exception));
        return problemDetail;
    }

    private static List<String> stackTraceToList(Throwable throwable) {
        return Arrays.stream(throwable.getStackTrace())
                .map(StackTraceElement::toString)
                .toList();
    }
}
