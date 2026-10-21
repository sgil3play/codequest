package com.codequest.quiz;

import static com.codequest.progress.ProgressService.DEFAULT_USER;

import com.codequest.domain.Track;
import com.codequest.progress.ProgressService;
import com.codequest.quiz.dto.QuizAnswerResult;
import com.codequest.quiz.dto.QuizQuestionView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quizzes")
@Tag(name = "Conceptual", description = "Banco de preguntas teoricas de Spring, JPA, SQL, microservicios y SOAP")
public class QuizController {

	private final QuizCatalog catalog;
	private final ProgressService progressService;

	public QuizController(QuizCatalog catalog, ProgressService progressService) {
		this.catalog = catalog;
		this.progressService = progressService;
	}

	@GetMapping
	@Operation(summary = "Lista el banco de preguntas agrupado por tema")
	public Map<String, List<QuizQuestionView>> list(@RequestParam(required = false) Track track) {
		List<QuizDefinition> quizzes = (track == null) ? catalog.findAll() : catalog.findByTrack(track);
		return quizzes.stream()
				.collect(Collectors.groupingBy(QuizDefinition::topic, LinkedHashMap::new, Collectors.toList()))
				.entrySet().stream()
				.collect(Collectors.toMap(
						Map.Entry::getKey,
						entry -> entry.getValue().stream().map(this::toView).toList(),
						(a, b) -> a,
						LinkedHashMap::new));
	}

	@GetMapping("/{id}")
	@Operation(summary = "Detalle de una pregunta")
	public QuizQuestionView get(@PathVariable String id) {
		return toView(catalog.get(id));
	}

	@PostMapping("/{id}/answer")
	@Operation(summary = "Responde una pregunta y devuelve la justificacion")
	public QuizAnswerResult answer(@PathVariable String id, @Valid @RequestBody AnswerRequest request,
			@RequestParam(required = false, defaultValue = DEFAULT_USER) String user) {
		QuizDefinition quiz = catalog.get(id);
		boolean correct = request.selectedIndex() == quiz.correctIndex();

		progressService.recordAttempt(user, quiz.id(), quiz.track(), correct ? 1 : 0, quiz.xp(), correct);

		return new QuizAnswerResult(correct, quiz.correctIndex(), quiz.explanation(), correct ? quiz.xp() : 0);
	}

	private QuizQuestionView toView(QuizDefinition quiz) {
		return new QuizQuestionView(quiz.id(), quiz.topic(), quiz.question(), quiz.options(), quiz.difficulty(),
				quiz.tags(), quiz.xp());
	}

	/**
	 * @param selectedIndex indice de la opcion elegida
	 */
	public record AnswerRequest(
			@NotNull @Min(0) @Max(20) Integer selectedIndex) {
	}
}
