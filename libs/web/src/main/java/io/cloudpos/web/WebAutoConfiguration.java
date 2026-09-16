package io.cloudpos.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import(ProblemDetailsHandler.class)
public class WebAutoConfiguration {
}
