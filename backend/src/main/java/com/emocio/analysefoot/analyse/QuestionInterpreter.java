package com.emocio.analysefoot.analyse;

/**
 * Interprète une question libre en question du catalogue (tool Claude rank). Ne lève jamais d'exception :
 * en cas de doute, d'erreur ou de réponse invalide, renvoie {@link Interpretation#none}.
 */
public interface QuestionInterpreter {

    Interpretation interpret(String question);

    /**
     * Mode libre, pour une question sans rapport avec le catalogue : le modèle appelle des tools de lecture (allers
     * et retours LLM, tool) puis rédige une réponse courte. Null si le mode n'est pas disponible ou n'aboutit pas.
     */
    default String answerFreely(String question) {
        return null;
    }

    /** Faux sans clé d'API : l'assistant n'est alors jamais consulté. */
    boolean enabled();
}
