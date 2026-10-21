package com.codequest.progress;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio del progreso por elemento del catalogo.
 *
 * <p>La clave primaria es {@link ItemProgressId}, un record con los campos
 * {@code username} e {@code itemId}. Spring Data resuelve las rutas
 * anidadas escribiendo el nombre del embeddable delante del atributo:
 * {@code findByIdUsername} corresponde a {@code id.username}.
 */
public interface ItemProgressRepository extends JpaRepository<ItemProgress, ItemProgressId> {

	List<ItemProgress> findByIdUsername(String username);

	Optional<ItemProgress> findByIdUsernameAndIdItemId(String username, String itemId);

	long countByIdUsernameAndStatus(String username, ItemProgress.Status status);
}
