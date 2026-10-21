package com.codequest.quiz;

import com.codequest.common.NotFoundException;
import com.codequest.common.config.CatalogReader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Catalogo de preguntas conceptuales, cargado desde {@code data/quizzes.yaml}. */
@Component
public class QuizCatalog {

	private static final Logger log = LoggerFactory.getLogger(QuizCatalog.class);
	private static final String RESOURCE = "data/quizzes.yaml";

	private final Map<String, QuizDefinition> byId;

	public QuizCatalog(CatalogReader reader) {
		this.byId = load(reader);
		log.info("Catalogo de preguntas conceptuales cargado: {} preguntas", byId.size());
	}

	private Map<String, QuizDefinition> load(CatalogReader reader) {
		List<QuizDefinition> quizzes = reader.readList(RESOURCE, QuizDefinition.class);
		return Collections.unmodifiableMap(quizzes.stream()
				.collect(Collectors.toMap(QuizDefinition::id, Function.identity(), (a, b) -> a)));
	}

	public List<QuizDefinition> findAll() {
		return byId.values().stream()
				.sorted((a, b) -> {
					int byTopic = a.topic().compareTo(b.topic());
					return byTopic != 0 ? byTopic : a.id().compareTo(b.id());
				})
				.toList();
	}

	public Optional<QuizDefinition> find(String id) {
		return Optional.ofNullable(byId.get(id));
	}

	public QuizDefinition get(String id) {
		return find(id).orElseThrow(() -> new NotFoundException("No existe la pregunta '" + id + "'"));
	}

	/** Agrupa las preguntas por tema, para pintarlas en secciones. */
	public Map<String, List<QuizDefinition>> groupByTopic() {
		return findAll().stream().collect(Collectors.groupingBy(QuizDefinition::topic, LinkedHashMap::new,
				Collectors.toList()));
	}
}
