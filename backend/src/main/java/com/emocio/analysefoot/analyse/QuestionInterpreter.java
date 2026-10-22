package com.emocio.analysefoot.analyse;

/**
 * Assistant LLM de l'onglet Analyse : interprète librement une saisie et y répond en plusieurs allers et retours avec
 * les tools de lecture. Ne lève jamais d'exception : en cas d'erreur, de délai dépassé ou de réponse vide, renvoie null.
 */
public interface QuestionInterpreter {

    /** La réponse courte du modèle à la saisie, ou null s'il n'aboutit pas. */
    String answerFreely(String question);

    /** Faux sans clé d'API : l'assistant n'est alors jamais consulté. */
    boolean enabled();
}
