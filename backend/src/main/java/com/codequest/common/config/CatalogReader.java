package com.codequest.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Lector de catalogos en YAML.
 *
 * <p>Los catalogos (retos, preguntas, ejercicios de diseno) son ficheros de
 * contenido versionables dentro del jar. Se leen con un {@link ObjectMapper}
 * propio, con formato YAML, para no depender de la configuracion de Jackson de
 * la API.
 *
 * <p>Este lector NO es un bean de tipo {@code ObjectMapper} a proposito: Spring
 * Boot desactiva su configuracion automatica de Jackson en cuanto existe
 * cualquier bean de ese tipo, y la API pasaria a serializar en YAML en lugar
 * de JSON. Al ser una clase propia, el mapper de la API queda intacto.
 */
@Component
public class CatalogReader {

	private final ObjectMapper mapper;

	public CatalogReader() {
		YAMLFactory factory = new YAMLFactory();
		this.mapper = new ObjectMapper(factory);
		this.mapper.registerModule(new JavaTimeModule());
	}

	/**
	 * Lee una lista de objetos desde un recurso del classpath.
	 *
	 * @param resource ruta del recurso, por ejemplo {@code data/katas.yaml}
	 * @param type     tipo de cada elemento de la lista
	 * @param <T>      tipo de cada elemento
	 * @return la lista leida, nunca {@code null}
	 * @throws IllegalStateException si el recurso no existe o no se puede
	 *                               deserializar
	 */
	public <T> List<T> readList(String resource, Class<T> type) {
		CollectionType listType = mapper.getTypeFactory().constructCollectionType(List.class, type);
		try (InputStream in = new ClassPathResource(resource).getInputStream()) {
			return mapper.readValue(in, listType);
		}
		catch (IOException ex) {
			throw new IllegalStateException("No se pudo cargar " + resource, ex);
		}
	}
}
