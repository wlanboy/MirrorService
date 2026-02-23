package com.wlanboy.mirrorservice;

import java.net.URI;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
public class RouterConfig {

    @Bean
    public RouterFunction<ServerResponse> indexRouter() {
        return RouterFunctions.route(
            RequestPredicates.GET("/"),
            request -> ServerResponse.temporaryRedirect(URI.create("/index.html")).build()
        );
    }
}