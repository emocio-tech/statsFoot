package com.emocio.analysefoot.analyse;

import com.emocio.analysefoot.dto.AnalyseAnswer;
import com.emocio.analysefoot.dto.RankedEntry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/** Orchestration de la recherche : nom, puis assistant LLM en priorité, puis suggestions ; sans base ni réseau. */
class AnalyseServiceTest {

    /** Assistant factice qui compte ses appels. */
    private static final class FakeAssistant implements QuestionInterpreter {
        private final boolean enabled;
        private final Function<String, String> behaviour;
        private int calls;

        FakeAssistant(boolean enabled, Function<String, String> behaviour) {
            this.enabled = enabled;
            this.behaviour = behaviour;
        }

        @Override
        public String answerFreely(String question) {
            calls++;
            return behaviour.apply(question);
        }

        @Override
        public boolean enabled() {
            return enabled;
        }
    }

    private static AnalyseService service(QuestionInterpreter assistant) {
        return new AnalyseService(null, assistant) {
            @Override
            List<RankedEntry> entityNames(AnalyseCriterion.Scope scope) {
                return scope == AnalyseCriterion.Scope.TEAM
                        ? List.of(new RankedEntry(1, "TEAM", 33, "France", null, null, "", null, null))
                        : List.of(new RankedEntry(1, "PLAYER", 664, "Kylian MBAPPE", "France", null, "", null, null));
            }
        };
    }

    @Test
    void aNameGivesItsReportWithoutCallingTheAssistant() {
        FakeAssistant assistant = new FakeAssistant(true, q -> "ne devrait pas servir");

        AnalyseAnswer answer = service(assistant).answer("Mbappé");

        assertThat(answer.status()).isEqualTo("FOUND");
        assertThat(answer.top()).extracting(RankedEntry::id).containsExactly(664);
        assertThat(assistant.calls).isZero();
    }

    @Test
    void anythingElseGoesToTheAssistantFirstEvenWhenItLooksLikeAPredefinedQuestion() {
        FakeAssistant assistant = new FakeAssistant(true, q -> "  Mbappé a marqué 10 buts.  ");

        AnalyseAnswer answer = service(assistant).answer("quel est le meilleur buteur du tournoi ?");

        assertThat(assistant.calls).isEqualTo(1);
        assertThat(answer.status()).isEqualTo("FREE_TEXT");
        assertThat(answer.text()).isEqualTo("Mbappé a marqué 10 buts.");
        assertThat(answer.interpretation().source()).isEqualTo("ASSISTANT");
        assertThat(answer.top()).isEmpty();
    }

    @Test
    void withoutAnAssistantTheSuggestionsAreProposed() {
        FakeAssistant assistant = new FakeAssistant(false, q -> "inutile");

        AnalyseAnswer answer = service(assistant).answer("quel est le meilleur buteur du tournoi ?");

        assertThat(assistant.calls).isZero();
        assertThat(answer.status()).isEqualTo("NOT_RECOGNIZED");
        assertThat(answer.candidates()).hasSize(AnalyseCriterion.values().length);
    }

    @Test
    void anAssistantThatDoesNotAnswerFallsBackToTheSuggestions() {
        assertThat(service(new FakeAssistant(true, q -> null)).answer("zzz ?").status()).isEqualTo("NOT_RECOGNIZED");
        assertThat(service(new FakeAssistant(true, q -> "  ")).answer("zzz ?").status()).isEqualTo("NOT_RECOGNIZED");
        assertThat(service(new FakeAssistant(true, q -> {
            throw new IllegalStateException("panne");
        })).answer("zzz ?").status()).isEqualTo("NOT_RECOGNIZED");
    }

    @Test
    void thereIsNoCallCapPerMinute() {
        FakeAssistant assistant = new FakeAssistant(true, q -> "ok");
        AnalyseService service = service(assistant);

        for (int i = 0; i < 50; i++) {
            assertThat(service.answer("question " + i + " ?").status()).isEqualTo("FREE_TEXT");
        }
        assertThat(assistant.calls).isEqualTo(50);
    }

    @Test
    void theAnswerCarriesTheTraceOfTheExchanges() {
        AnalyseAnswer answer = service(new FakeAssistant(true, q -> "ok")).answer("une question ?");

        assertThat(answer.trace()).extracting(t -> t.label())
                .contains("Question reçue", "Question confiée à l'assistant");
    }
}
