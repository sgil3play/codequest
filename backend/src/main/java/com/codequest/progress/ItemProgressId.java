package com.codequest.progress;

import jakarta.persistence.Embeddable;
import java.io.Serializable;

/**
 * Clave compuesta de {@link ItemProgress}.
 *
 * <p>Es un record, asi que el `equals` y el `hashCode` los genera el
 * compilador y son correctos: es justo lo que Hibernate exige a la clase de
 * una clave compuesta.
 */
@Embeddable
public record ItemProgressId(String username, String itemId) implements Serializable {
}
