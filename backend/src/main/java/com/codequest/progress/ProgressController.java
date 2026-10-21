package com.codequest.progress;

import static com.codequest.progress.ProgressService.DEFAULT_USER;

import com.codequest.progress.dto.ItemProgressView;
import com.codequest.progress.dto.ProgressSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/progress")
@Tag(name = "Progreso", description = "Experiencia, niveles, rachas y estado de cada elemento")
public class ProgressController {

	private final ProgressService service;

	public ProgressController(ProgressService service) {
		this.service = service;
	}

	@GetMapping("/summary")
	@Operation(summary = "Resumen de progreso para el dashboard")
	public ProgressSummary summary(@RequestParam(required = false, defaultValue = DEFAULT_USER) String user) {
		return service.getSummary(user);
	}

	@GetMapping("/items")
	@Operation(summary = "Estado de progreso de cada elemento")
	public List<ItemProgressView> items(@RequestParam(required = false, defaultValue = DEFAULT_USER) String user) {
		return service.getItemProgressView(user);
	}
}
