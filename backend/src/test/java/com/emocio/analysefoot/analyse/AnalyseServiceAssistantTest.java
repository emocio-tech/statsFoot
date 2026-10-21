package com.emocio.analysefoot.analyse;

import com.emocio.analysefoot.dto.AnalyseAnswer;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/** Orchestration mots-clés puis assistant, sans base ni réseau (interprète factice). */
class AnalyseServiceAssistantTest {

    /** Interprète factice qui compte ses appels. */
    private static final class FakeInterpreter implements QuestionInterpreter {
        private final boolean enabled;
        private final Function<String, Interpretation> behaviour;
        private int calls;

        FakeInterpreter(boolean enabled, Function<String, Interpretation> behaviour) {
            this.enabled = enabled;
            this.behaviour = behaviour;
        }

        @Override
        public Interpretation interpret(String question) {
            calls++;
            return behaviour.apply(question);
        }

        @Override
        public boolean enabled() {
            return enabled;
        }
    }

    private static AnalyseService service(QuestionInterpreter interpreter, int maxPerMinute) {
        return new AnalyseService(null, new QuestionMatcher(), interpreter,
                new AssistantRateLimiter(maxPerMinute, Clock.systemUTC()));
    }

    private static Interpretation recognized(AnalyseCriterion criterion) {
        return Interpretation.recognized(criterion, Interpretation.Source.ASSISTANT);
    }

    @Test
    void keywordMatchNeverCallsTheAssistant() {
        FakeInterpreter assistant = new FakeInterpreter(true, q -> recognized(AnalyseCriterion.BEST_TEAM));
        Interpretation result = service(assistant, 30).resolve("qui a pris le plus de jaunes");

        assertThat(assistant.calls).isZero();
        assertThat(result).isEqualTo(Interpretation.recognized(AnalyseCriterion.MOST_YELLOW_CARDS, Interpretation.Source.KEYWORDS));
    }

    @Test
    void unrecognizedQuestionIsInterpretedByTheAssistant() {
        FakeInterpreter assistant = new FakeInterpreter(true, q -> recognized(AnalyseCriterion.TOP_SCORING_TEAM));
        Interpretation result = service(assistant, 30).resolve("quelle nation a planté le plus ?");

        assertThat(assistant.calls).isEqualTo(1);
        assertThat(result).isEqualTo(recognized(AnalyseCriterion.TOP_SCORING_TEAM));
    }

    @Test
    void ambiguousQuestionTakesTheAssistantAlternatives() {
        List<AnalyseCriterion> both = List.of(AnalyseCriterion.LEAST_FAIR_PLAY_PLAYER, AnalyseCriterion.LEAST_FAIR_PLAY_TEAM);
        FakeInterpreter assistant = new FakeInterpreter(true, q -> Interpretation.ambiguous(both, Interpretation.Source.ASSISTANT));
        AnalyseAnswer answer = service(assistant, 30).answer("le moins fair-play");

        assertThat(assistant.calls).isEqualTo(1);
        assertThat(answer.status()).isEqualTo("AMBIGUOUS");
        assertThat(answer.candidates()).extracting(q -> q.id()).containsExactly("LEAST_FAIR_PLAY_PLAYER", "LEAST_FAIR_PLAY_TEAM");
        assertThat(answer.interpretation().source()).isEqualTo("ASSISTANT");
    }

    @Test
    void assistantNoneKeepsTheKeywordResult() {
        FakeInterpreter assistant = new FakeInterpreter(true, q -> Interpretation.none(Interpretation.Source.ASSISTANT));
        AnalyseService service = service(assistant, 30);

        AnalyseAnswer unknown = service.answer("quel est le salaire de Mbappé ?");
        assertThat(unknown.status()).isEqualTo("NOT_RECOGNIZED");
        assertThat(unknown.candidates()).hasSize(8);
        assertThat(unknown.interpretation().source()).isEqualTo("KEYWORDS");

        AnalyseAnswer ambiguous = service.answer("le moins fair-play");
        assertThat(ambiguous.status()).isEqualTo("AMBIGUOUS");
        assertThat(ambiguous.candidates()).hasSize(2);
    }

    @Test
    void disabledAssistantIsNeverCalledAndKeywordsAnswerAsBefore() {
        FakeInterpreter assistant = new FakeInterpreter(false, q -> recognized(AnalyseCriterion.BEST_TEAM));
        AnalyseService service = service(assistant, 30);
        QuestionMatcher matcher = new QuestionMatcher();

        for (String question : List.of("meilleur buteur", "qui a pris le plus de jaunes", "la meilleure attaque",
                "qui est champion ?", "le joueur le plus propre", "quelle nation a planté le plus ?")) {
            assertThat(service.resolve(question).criterion()).isEqualTo(
                    matcher.match(question).recognized() ? matcher.match(question).candidates().getFirst() : null);
        }
        assertThat(assistant.calls).isZero();
        assertThat(service.assistantEnabled()).isFalse();
    }

    @Test
    void failingAssistantFallsBackToKeywords() {
        FakeInterpreter assistant = new FakeInterpreter(true, q -> { throw new IllegalStateException("panne"); });
        assertThat(service(assistant, 30).resolve("quelle nation a planté le plus ?"))
                .isEqualTo(Interpretation.none(Interpretation.Source.KEYWORDS));
    }

    @Test
    void rateLimitStopsCallingTheAssistant() {
        FakeInterpreter assistant = new FakeInterpreter(true, q -> recognized(AnalyseCriterion.TOP_SCORER));
        AnalyseService service = service(assistant, 2);
        for (int i = 0; i < 5; i++) {
            service.resolve("meilleur buteure du mondiale");
        }
        assertThat(assistant.calls).isEqualTo(2);
    }

    @Test
    void interpretationSourceIsReported() {
        FakeInterpreter assistant = new FakeInterpreter(true, q -> recognized(AnalyseCriterion.TOP_SCORER));
        AnalyseService service = service(assistant, 30);

        assertThat(service.resolve("meilleur buteur").source()).isEqualTo(Interpretation.Source.KEYWORDS);
        assertThat(service.resolve("meilleur buteure du mondiale").source()).isEqualTo(Interpretation.Source.ASSISTANT);
    }
}
