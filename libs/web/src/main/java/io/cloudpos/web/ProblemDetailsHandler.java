package io.cloudpos.web;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ProblemDetailsHandler {

    private static final String DOCS = "https://docs.cloudpos.io/errors/";

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handle(ApiException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
        pd.setType(java.net.URI.create(DOCS + e.code().toLowerCase().replace('_', '-')));
        pd.setProperty("code", e.code());
        return pd;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleMissingTenant(IllegalStateException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                org.springframework.http.HttpStatus.BAD_REQUEST, e.getMessage());
        pd.setProperty("code", "TENANT_CONTEXT_MISSING");
        return pd;
    }
}
