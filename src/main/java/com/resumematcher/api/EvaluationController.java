package com.resumematcher.api;

import com.resumematcher.service.EvaluationResult;
import com.resumematcher.service.EvaluationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/evaluations")
public class EvaluationController {

    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @PostMapping
    public EvaluationResponse evaluate(
            @Valid @RequestBody EvaluationRequest request,
            @RequestParam(defaultValue = "false") boolean detail) {
        EvaluationResult result = evaluationService.evaluate(request);
        return new EvaluationResponse(
                result.score(),
                result.explanation(),
                detail ? result.requirements() : null);
    }
}
