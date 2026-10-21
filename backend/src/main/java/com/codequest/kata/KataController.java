package com.codequest.kata;

import com.codequest.domain.Track;
import com.codequest.kata.dto.KataDetail;
import com.codequest.kata.dto.KataSummary;
import com.codequest.kata.dto.SubmissionResult;
import com.codequest.kata.dto.SubmitRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/katas")
@Tag(name = "Katas", description = "Catalogo de retos de codigo y motor de ejecucion")
public class KataController {

	private final KataService service;

	public KataController(KataService service) {
		this.service = service;
	}

	@GetMapping
	@Operation(summary = "Lista el catalogo de retos")
	public List<KataSummary> list(@RequestParam(required = false) Track track,
			@RequestParam(required = false, defaultValue = "sergio") String user) {
		List<KataSummary> katas = service.list(user);
		if (track == null) {
			return katas;
		}
		return katas.stream().filter(k -> k.track() == track).toList();
	}

	@GetMapping("/{id}")
	@Operation(summary = "Detalle de un reto")
	public KataDetail get(@PathVariable String id, @RequestParam(required = false, defaultValue = "sergio") String user) {
		return service.get(id, user);
	}

	@PostMapping("/{id}/submit")
	@Operation(summary = "Compila y ejecuta la solucion contra la suite de test")
	public SubmissionResult submit(@PathVariable String id, @Valid @RequestBody SubmitRequest request) {
		return service.submit(id, request);
	}

	@GetMapping("/{id}/solution")
	@Operation(summary = "Revela la solucion comentada")
	public String solution(@PathVariable String id) {
		return service.revealSolution(id);
	}
}
