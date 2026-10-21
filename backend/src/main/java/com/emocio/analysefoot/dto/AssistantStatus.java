package com.emocio.analysefoot.dto;

/** État de l'assistant d'interprétation : faux sans clé ANTHROPIC_API_KEY. */
public record AssistantStatus(boolean enabled) {
}
