package com.emocio.analysefoot.dto;

/** État de l'assistant d'interprétation : faux sans clé LLM_KEY. */
public record AssistantStatus(boolean enabled) {
}
