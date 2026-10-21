package com.emocio.analysefoot.analyse;

/** Interprète utilisé sans clé LLM_KEY : aucune interprétation, aucun appel externe. */
public class DisabledQuestionInterpreter implements QuestionInterpreter {

    @Override
    public Interpretation interpret(String question) {
        return Interpretation.none(Interpretation.Source.ASSISTANT);
    }

    @Override
    public boolean enabled() {
        return false;
    }
}
