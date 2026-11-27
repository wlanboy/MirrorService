package com.wlanboy.mirrorservice.config;

import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI openApi(ObjectProvider<BuildProperties> buildProperties) {
        // Version aus META-INF/build-info.properties (spring-boot-maven-plugin, Goal build-info).
        String version = buildProperties.stream().map(BuildProperties::getVersion).findFirst().orElse("dev");
        return new OpenAPI().info(new Info()
            .title("MirrorService")
            .version(version)
            .description("""
                Testdienst für Netzwerk- und Proxy-Setups: spiegelt Requests mit konfigurierbarer Antwort (/mirror), \
                gibt empfangene Requests zurück (/echo) und prüft DNS-Auflösung und Erreichbarkeit (/resolve, /ping)."""));
    }

    /**
     * Bedient eine Controller-Methode mehrere HTTP-Methoden, nummeriert springdoc die operationIds
     * durch (echo, echo_1, echo_2, ...). Stattdessen bekommt jede die HTTP-Methode angehängt:
     * echo_get, echo_post, ...
     */
    @Bean
    OpenApiCustomizer operationIdsWithHttpMethod() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            Map<String, Long> countPerBase = openApi.getPaths().values().stream()
                .flatMap(item -> item.readOperations().stream())
                .map(op -> baseId(op.getOperationId()))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
            openApi.getPaths().values().forEach(item -> item.readOperationsMap().forEach((method, op) -> {
                String base = baseId(op.getOperationId());
                if (countPerBase.getOrDefault(base, 0L) > 1) {
                    op.setOperationId(base + "_" + method.name().toLowerCase(Locale.ROOT));
                }
            }));
        };
    }

    private static String baseId(String operationId) {
        return operationId == null ? "" : operationId.replaceFirst("_\\d+$", "");
    }

    /**
     * Entfernt den Request-Body aus GET und HEAD von /echo/**. springdoc hängt den Body aus der
     * Operation-Annotation an alle Methoden, Browser schicken bei GET und HEAD aber keinen Body,
     * und Swagger UI scheitert dann beim Ausführen.
     */
    @Bean
    OpenApiCustomizer echoWithoutBodyOnGet() {
        return openApi -> {
            PathItem echo = openApi.getPaths() == null ? null : openApi.getPaths().get("/echo/**");
            if (echo == null) {
                return;
            }
            if (echo.getGet() != null) {
                echo.getGet().setRequestBody(null);
            }
            if (echo.getHead() != null) {
                echo.getHead().setRequestBody(null);
            }
        };
    }
}
