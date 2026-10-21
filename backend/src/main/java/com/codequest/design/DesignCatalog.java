package com.codequest.design;

import com.codequest.common.NotFoundException;
import com.codequest.common.config.CatalogReader;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Catalogo de ejercicios de diseno, cargado desde {@code data/design.yaml}. */
@Component
public class DesignCatalog {

	private static final Logger log = LoggerFactory.getLogger(DesignCatalog.class);
	private static final String RESOURCE = "data/design.yaml";

	private final Map<String, DesignDefinition> byId;

	public DesignCatalog(CatalogReader reader) {
		this.byId = load(reader);
		log.info("Catalogo de ejercicios de diseno cargado: {} ejercicios", byId.size());
	}

	private Map<String, DesignDefinition> load(CatalogReader reader) {
		List<DesignDefinition> exercises = reader.readList(RESOURCE, DesignDefinition.class);
		return Collections.unmodifiableMap(exercises.stream()
				.collect(Collectors.toMap(DesignDefinition::id, Function.identity(), (a, b) -> a)));
	}

	public List<DesignDefinition> findAll() {
		return byId.values().stream()
				.sorted((a, b) -> {
					int byDifficulty = a.difficulty().compareTo(b.difficulty());
					return byDifficulty != 0 ? byDifficulty : a.id().compareTo(b.id());
				})
				.toList();
	}

	public Optional<DesignDefinition> find(String id) {
		return Optional.ofNullable(byId.get(id));
	}

	public DesignDefinition get(String id) {
		return find(id).orElseThrow(() -> new NotFoundException("No existe el ejercicio '" + id + "'"));
	}
}
