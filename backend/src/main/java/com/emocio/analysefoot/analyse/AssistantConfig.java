package com.emocio.analysefoot.analyse;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/**
 * Assistant d'interprétation (tool Claude rank) : activé seulement si une clé {@code ANTHROPIC_API_KEY} est fournie.
 * Délai court et aucune nouvelle tentative : en cas d'échec, les mots-clés répondent.
 */
@Configuration
@EnableConfigurationProperties(AssistantConfig.Properties.class)
public class AssistantConfig {

    @ConfigurationProperties("analyse.assistant")
    public record Properties(String apiKey, String model, Duration timeout, int maxPerMinute) {
    }

    @Bean
    QuestionInterpreter questionInterpreter(Properties properties) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            return new DisabledQuestionInterpreter();
        }
        return new ClaudeQuestionInterpreter(AnthropicOkHttpClient.builder()
                .apiKey(properties.apiKey())
                .timeout(properties.timeout())
                .maxRetries(0)
                .build(), properties.model());
    }

    @Bean
    AssistantRateLimiter assistantRateLimiter(Properties properties) {
        return new AssistantRateLimiter(properties.maxPerMinute(), Clock.systemUTC());
    }
}
