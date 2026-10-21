package com.emocio.analysefoot.controller;

import com.emocio.analysefoot.analyse.AnalyseCriterion;
import com.emocio.analysefoot.analyse.AnalyseService;
import com.emocio.analysefoot.dto.AnalyseQuestion;
import com.emocio.analysefoot.dto.AssistantStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Onglet Analyse : questions du catalogue et réponses. */
@RestController
@RequestMapping("/api/analyse")
public class AnalyseController {

    private final AnalyseService analyseService;

    public AnalyseController(AnalyseService analyseService) {
        this.analyseService = analyseService;
    }

    /** Question libre, reconnue par mots-clés. */
    @GetMapping
    public ResponseEntity<?> search(@RequestParam(name = "q", required = false) String question) {
        if (question == null || question.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Veuillez saisir une question."));
        }
        return ResponseEntity.ok(analyseService.answer(question));
    }

    /** Indique si l'assistant (tool Claude rank) est configuré, pour afficher la mention de transmission. */
    @GetMapping("/assistant")
    public AssistantStatus assistant() {
        return new AssistantStatus(analyseService.assistantEnabled());
    }

    @GetMapping("/questions")
    public List<AnalyseQuestion> questions() {
        return analyseService.questions();
    }

    @GetMapping("/{questionId}")
    public ResponseEntity<?> answer(@PathVariable String questionId) {
        return AnalyseCriterion.fromId(questionId)
                .<ResponseEntity<?>>map(criterion -> ResponseEntity.ok(analyseService.answer(criterion)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Question inconnue.")));
    }
}
