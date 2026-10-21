package com.codequest.design;

import com.codequest.domain.Difficulty;
import com.codequest.domain.Track;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/design")
@Tag(name = "Diseno de sistemas", description = "Ejercicios de arquitectura y escalabilidad")
public class DesignController {

	private final DesignCatalog catalog;

	public DesignController(DesignCatalog catalog) {
		this.catalog = catalog;
	}

	@GetMapping
	@Operation(summary = "Lista los ejercicios de diseno")
	public List<DesignSummary> list() {
		return catalog.findAll().stream()
				.map(d -> new DesignSummary(d.id(), d.title(), d.difficulty(), d.track(), d.tags(), d.xp(),
						d.estimatedMinutes()))
				.toList();
	}

	@GetMapping("/{id}")
	@Operation(summary = "Detalle de un ejercicio, sin la respuesta de referencia")
	public DesignDetail get(@PathVariable String id) {
		DesignDefinition d = catalog.get(id);
		return new DesignDetail(d.id(), d.title(), d.question(), d.scenario(), d.requirements(), d.questions(),
				d.hints(), d.difficulty(), d.track(), d.tags(), d.xp(), d.estimatedMinutes());
	}

	@GetMapping("/{id}/reference")
	@Operation(summary = "Revela la respuesta de referencia")
	public String reference(@PathVariable String id) {
		return catalog.get(id).referenceAnswer();
	}

	/**
	 * @param id              identificador
	 * @param title           titulo
	 * @param difficulty      dificultad
	 * @param track           pista
	 * @param tags            etiquetas
	 * @param xp              recompensa
	 * @param estimatedMinutes estimacion de tiempo
	 */
	public record DesignSummary(String id, String title, Difficulty difficulty, Track track, List<String> tags, int xp,
			int estimatedMinutes) {
	}

	/**
	 * @param id              identificador
	 * @param title           titulo
	 * @param question        enunciado
	 * @param scenario        contexto
	 * @param requirements    requisitos
	 * @param questions       preguntas a responder
	 * @param hints           pistas
	 * @param difficulty      dificultad
	 * @param track           pista
	 * @param tags            etiquetas
	 * @param xp              recompensa
	 * @param estimatedMinutes estimacion de tiempo
	 */
	public record DesignDetail(String id, String title, String question, String scenario, List<String> requirements,
			List<String> questions, List<String> hints, Difficulty difficulty, Track track, List<String> tags, int xp,
			int estimatedMinutes) {
	}
}
