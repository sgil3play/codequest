package com.codequest.kata;

import com.codequest.common.NotFoundException;
import com.codequest.common.config.CatalogReader;
import com.codequest.domain.Track;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Catalogo de retos. Se lee una sola vez al arrancar y se mantiene en memoria:
 * el catalogo es pequeno y de solo lectura, asi que no tiene sentido ir a la
 * base de datos en cada peticion.
 */
@Component
public class KataCatalog {

	private static final Logger log = LoggerFactory.getLogger(KataCatalog.class);
	private static final String RESOURCE = "data/katas.yaml";

	private final Map<String, KataDefinition> byId;

	public KataCatalog(CatalogReader reader) {
		this.byId = load(reader);
		log.info("Catalogo de retos cargado: {} retos", byId.size());
	}

	private Map<String, KataDefinition> load(CatalogReader reader) {
		List<KataDefinition> katas = reader.readList(RESOURCE, KataDefinition.class);
		Map<String, KataDefinition> index = katas.stream()
				.collect(Collectors.toMap(KataDefinition::id, Function.identity(), (a, b) -> {
					log.warn("Id duplicado en el catalogo de retos: {}", a.id());
					return a;
				}));
		return Collections.unmodifiableMap(index);
	}

	public List<KataDefinition> findAll() {
		return byId.values().stream()
				.sorted((a, b) -> {
					int byTrack = a.track().compareTo(b.track());
					return byTrack != 0 ? byTrack : a.id().compareTo(b.id());
				})
				.toList();
	}

	public List<KataDefinition> findByTrack(Track track) {
		return findAll().stream().filter(k -> k.track() == track).toList();
	}

	public Optional<KataDefinition> find(String id) {
		return Optional.ofNullable(byId.get(id));
	}

	public KataDefinition get(String id) {
		return find(id).orElseThrow(() -> new NotFoundException("No existe el reto '" + id + "'"));
	}
}
