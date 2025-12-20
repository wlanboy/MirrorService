package com.wlanboy.mirrorservice.controller;

import java.lang.reflect.RecordComponent;
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

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(MirrorInstruction.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {

        HttpServletRequest request = (HttpServletRequest) webRequest.getNativeRequest();

        // 1. JSON-Body Handling (unverändert)
        if (isJsonRequest(request)) {
            String body = request.getReader().lines().collect(Collectors.joining());
            if (!body.isBlank()) {
                return objectMapper.readValue(body, MirrorInstruction.class);
            }
        }

        // 2. Dynamisches Mapping für GET/Parameter
        RecordComponent[] components = MirrorInstruction.class.getRecordComponents();
        Object[] values = new Object[components.length];

        for (int i = 0; i < components.length; i++) {
            String name = components[i].getName();
            Class<?> type = components[i].getType();

            if (type == Map.class) {
                values[i] = extractMapFromParameters(webRequest, name);
            } else {
                values[i] = convertValue(webRequest.getParameter(name), type);
            }
        }

        return MirrorInstruction.class.getDeclaredConstructors()[0].newInstance(values);
    }

    private Map<String, String> extractMapFromParameters(NativeWebRequest webRequest, String prefix) {
        Map<String, String> result = new HashMap<>();
        // Gehe durch alle Parameter-Namen (z.B. "responseHeaders[Content-Type]")
        webRequest.getParameterNames().forEachRemaining(paramName -> {
            if (paramName.startsWith(prefix + "[") && paramName.endsWith("]")) {
                // Extrahiere den Key zwischen den Klammern
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

    private Object convertValue(String value, Class<?> type) {
        if (value == null) {
            if (type == int.class)
                return 0;
            if (type == Map.class)
                return Map.of();
            return null;
        }
        if (type == int.class)
            return Integer.parseInt(value);
        return value;
    }
}