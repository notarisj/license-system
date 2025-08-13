package com.notaris.license_system.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Configuration
public class ApiKeyConfig {
    private final Set<String> whitelist;

    public ApiKeyConfig(@Value("${app.api.whitelist:}") String whitelistRaw) {
        if (whitelistRaw == null || whitelistRaw.isBlank()) {
            this.whitelist = Set.of();
        } else {
            this.whitelist = new HashSet<>(Arrays.asList(whitelistRaw.split("\\s*,\\s*")));
        }
    }

    public boolean isAuthorized(String apiKey) {
        return apiKey != null && whitelist.contains(apiKey);
    }
}