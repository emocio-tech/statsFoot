package com.emocio.analysefoot.analyse;

/**
 * Interprète une question libre en question du catalogue (tool Claude rank). Ne lève jamais d'exception :
 * en cas de doute, d'erreur ou de réponse invalide, renvoie {@link Interpretation#none}.
 */
public interface QuestionInterpreter {

    Interpretation interpret(String question);

    /** Faux sans clé d'API : l'assistant n'est alors jamais consulté. */
    boolean enabled();
}
