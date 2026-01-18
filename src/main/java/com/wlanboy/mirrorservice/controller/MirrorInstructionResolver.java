package com.wlanboy.mirrorservice.controller;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;

public class MirrorInstructionResolver implements HandlerMethodArgumentResolver {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Constructor<MirrorInstruction> constructor;

    public MirrorInstructionResolver() {
        try {
            this.constructor = MirrorInstruction.class.getConstructor(
                int.class, int.class, String.class, Map.class
            );
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("MirrorInstruction constructor not found", e);
        }
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(MirrorInstruction.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {

        HttpServletRequest request = (HttpServletRequest) webRequest.getNativeRequest();

        // 1. JSON-Body Handling
        if (isJsonRequest(request)) {
            String body = request.getReader().lines().collect(Collectors.joining());
            if (!body.isBlank()) {
                return objectMapper.readValue(body, MirrorInstruction.class);
            }
        }

        // 2. Query-Parameter Mapping
        int statusCode = parseIntSafe(webRequest.getParameter("statusCode"), 0);
        int waitMs = parseIntSafe(webRequest.getParameter("waitMs"), 0);
        String responseBody = webRequest.getParameter("responseBody");
        Map<String, String> responseHeaders = extractMapFromParameters(webRequest, "responseHeaders");

        return constructor.newInstance(statusCode, waitMs, responseBody, responseHeaders);
    }

    private Map<String, String> extractMapFromParameters(NativeWebRequest webRequest, String prefix) {
        Map<String, String> result = new HashMap<>();
        webRequest.getParameterNames().forEachRemaining(paramName -> {
            if (paramName.startsWith(prefix + "[") && paramName.endsWith("]")) {
                String key = paramName.substring(prefix.length() + 1, paramName.length() - 1);
                result.put(key, webRequest.getParameter(paramName));
            }
        });
        return result;
    }

    private boolean isJsonRequest(HttpServletRequest request) {
        String ct = request.getContentType();
        return ct != null && ct.contains("application/json");
    }

    private int parseIntSafe(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}