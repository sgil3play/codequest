package com.codequest.kata;

import com.codequest.kata.dto.CompletionRequest;
import com.codequest.kata.dto.CompletionResponse;
import com.codequest.kata.dto.KataDetail;
import com.codequest.kata.dto.KataSummary;
import com.codequest.kata.dto.SubmissionResult;
import com.codequest.kata.dto.SubmitRequest;
import com.codequest.progress.ItemProgress;
import com.codequest.progress.ProgressService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Orquesta el ciclo de un reto: consulta del catalogo, ejecucion del codigo
 * y actualizacion del progreso.
 */
@Service
public class KataService {

	private final KataCatalog catalog;
	private final KataRunner runner;
	private final ProgressService progressService;
	private final CompletionService completionService;

	public KataService(KataCatalog catalog, KataRunner runner, ProgressService progressService,
			CompletionService completionService) {
		this.catalog = catalog;
		this.runner = runner;
		this.progressService = progressService;
		this.completionService = completionService;
	}

	public List<KataSummary> list(String user) {
		// El progreso de todos los retos se resuelve en una sola consulta. Consultarlo
		// uno a uno haria un viaje a la base de datos por cada reto (N+1).
		Map<String, ItemProgress> progressByItem = progressService.progressByItem(user);
		return catalog.findAll().stream()
				.map(kata -> toSummary(kata, progressByItem.get(kata.id())))
				.toList();
	}

	public KataDetail get(String id, String user) {
		KataDefinition kata = catalog.get(id);
		ItemProgress progress = progressService.getItemProgress(user, id).orElse(null);
		return new KataDetail(
				kata.id(),
				kata.track(),
				kata.title(),
				kata.difficulty(),
				kata.tags(),
				kata.xp(),
				kata.estimatedMinutes(),
				kata.prompt(),
				kata.functionSignature(),
				kata.starterCode(),
				kata.solutionClass(),
				kata.timeLimitSeconds(),
				progress == null ? "NOT_STARTED" : progress.getStatus().name(),
				progress == null ? null : progress.getBestScore(),
				progress == null ? 0 : progress.getAttempts());
	}

	/**
	 * Ejecuta la solucion y actualiza el progreso.
	 */
	public SubmissionResult submit(String id, SubmitRequest request) {
		KataDefinition kata = catalog.get(id);
		String user = request.resolvedUser();

		SubmissionResult result = runner.run(kata, request.code());

		boolean completed = result.status() == SubmissionResult.Status.PASSED;
		progressService.recordAttempt(user, kata.id(), kata.track(), result.score(), kata.xp(), completed);

		return result;
	}

	/**
	 * Sugiere los miembros que se pueden escribir despues de un punto, para el
	 * autocompletado del editor.
	 */
	public CompletionResponse complete(String id, CompletionRequest request) {
		KataDefinition kata = catalog.get(id);
		return completionService.complete(kata.solutionClass(), request.code(), request.offset());
	}

	/**
	 * Revela la solucion comentada. Solo se expone de forma explicita: la UI
	 * lo pide cuando la persona se atasca, y el backend no lo impide, pero queda
	 * registrado en los logs para poder verlo en una sesion de mentoring.
	 */
	public String revealSolution(String id) {
		return catalog.get(id).explanation();
	}

	private KataSummary toSummary(KataDefinition kata, ItemProgress progress) {
		return new KataSummary(
				kata.id(),
				kata.track(),
				kata.title(),
				kata.difficulty(),
				kata.tags(),
				kata.xp(),
				kata.estimatedMinutes(),
				progress == null ? "NOT_STARTED" : progress.getStatus().name(),
				progress == null ? null : progress.getBestScore(),
				progress == null ? 0 : progress.getAttempts());
	}
}
