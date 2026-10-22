package com.emocio.analysefoot.analyse;

/** Assistant utilisé sans clé LLM_KEY : aucune réponse, aucun appel externe. */
public class DisabledQuestionInterpreter implements QuestionInterpreter {

    @Override
    public String answerFreely(String question) {
        return null;
    }

    @Override
    public boolean enabled() {
        return false;
    }
}
