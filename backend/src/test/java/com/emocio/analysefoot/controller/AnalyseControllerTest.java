package com.emocio.analysefoot.controller;

import com.emocio.analysefoot.analyse.AnalyseCriterion;
import com.emocio.analysefoot.analyse.AnalyseService;
import com.emocio.analysefoot.dto.AnalyseAnswer;
import com.emocio.analysefoot.dto.AnalyseInterpretation;
import com.emocio.analysefoot.dto.AnalyseQuestion;
import com.emocio.analysefoot.dto.RankedEntry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalyseController.class)
class AnalyseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyseService analyseService;

    @Test
    void questionsAreListedInSuggestionOrder() throws Exception {
        when(analyseService.questions()).thenReturn(Arrays.stream(AnalyseCriterion.values()).map(AnalyseCriterion::toQuestion).toList());

        mockMvc.perform(get("/api/analyse/questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[0].id").value("TOP_SCORER"))
                .andExpect(jsonPath("$[1].fictitious").value(true))
                .andExpect(jsonPath("$[7].id").value("MOST_FAIR_PLAY_PLAYER"));
    }

    @Test
    void suggestionIsAnswered() throws Exception {
        AnalyseQuestion question = AnalyseCriterion.TOP_SCORER.toQuestion();
        RankedEntry mbappe = new RankedEntry(1, "PLAYER", 664, "Kylian MBAPPE", "France",
                new BigDecimal("10"), "buts", new BigDecimal("698"), null);
        when(analyseService.answer(AnalyseCriterion.TOP_SCORER)).thenReturn(new AnalyseAnswer(
                "ANSWERED", question, AnalyseCriterion.TOP_SCORER.explanation(), false,
                List.of(mbappe), List.of(mbappe), List.of(), null));

        mockMvc.perform(get("/api/analyse/TOP_SCORER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANSWERED"))
                .andExpect(jsonPath("$.fictitious").value(false))
                .andExpect(jsonPath("$.explanation").isNotEmpty())
                .andExpect(jsonPath("$.winners[0].name").value("Kylian MBAPPE"))
                .andExpect(jsonPath("$.top[0].value").value(10))
                .andExpect(jsonPath("$.interpretation").isEmpty());
    }

    @Test
    void unknownQuestionIsNotFound() throws Exception {
        mockMvc.perform(get("/api/analyse/INCONNU"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Question inconnue."));
    }

    @Test
    void freeQuestionIsAnswered() throws Exception {
        when(analyseService.answer("qui a marqué le plus ?")).thenReturn(new AnalyseAnswer("ANSWERED",
                AnalyseCriterion.TOP_SCORER.toQuestion(), AnalyseCriterion.TOP_SCORER.explanation(), false,
                List.of(), List.of(), List.of(), new AnalyseInterpretation("ASSISTANT", "Meilleur buteur")));

        mockMvc.perform(get("/api/analyse").param("q", "qui a marqué le plus ?"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANSWERED"))
                .andExpect(jsonPath("$.question.id").value("TOP_SCORER"))
                .andExpect(jsonPath("$.interpretation.source").value("ASSISTANT"))
                .andExpect(jsonPath("$.interpretation.understoodAs").value("Meilleur buteur"));
    }

    @Test
    void ambiguousQuestionListsCandidates() throws Exception {
        when(analyseService.answer("le moins fair-play")).thenReturn(new AnalyseAnswer("AMBIGUOUS", null, null, false,
                List.of(), List.of(), List.of(AnalyseCriterion.LEAST_FAIR_PLAY_PLAYER.toQuestion(),
                AnalyseCriterion.LEAST_FAIR_PLAY_TEAM.toQuestion()), new AnalyseInterpretation("KEYWORDS", null)));

        mockMvc.perform(get("/api/analyse").param("q", "le moins fair-play"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AMBIGUOUS"))
                .andExpect(jsonPath("$.candidates.length()").value(2));
    }

    @Test
    void unrecognizedQuestionListsAllQuestions() throws Exception {
        when(analyseService.answer("quel temps fera-t-il ?")).thenReturn(new AnalyseAnswer("NOT_RECOGNIZED", null,
                null, false, List.of(), List.of(),
                Arrays.stream(AnalyseCriterion.values()).map(AnalyseCriterion::toQuestion).toList(),
                new AnalyseInterpretation("KEYWORDS", null)));

        mockMvc.perform(get("/api/analyse").param("q", "quel temps fera-t-il ?"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_RECOGNIZED"))
                .andExpect(jsonPath("$.candidates.length()").value(8));
    }

    @Test
    void blankQuestionIsRejected() throws Exception {
        mockMvc.perform(get("/api/analyse").param("q", "  "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
        mockMvc.perform(get("/api/analyse")).andExpect(status().isBadRequest());
    }

    @Test
    void assistantStatusIsExposed() throws Exception {
        when(analyseService.assistantEnabled()).thenReturn(true);
        mockMvc.perform(get("/api/analyse/assistant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
        when(analyseService.assistantEnabled()).thenReturn(false);
        mockMvc.perform(get("/api/analyse/assistant")).andExpect(jsonPath("$.enabled").value(false));
    }
}
