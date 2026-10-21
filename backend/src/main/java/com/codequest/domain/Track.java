package com.codequest.domain;

/**
 * Pistas de practica. Cada pista cubre un bloque de la oferta tecnica:
 * <ul>
 *   <li>{@link #JAVA_CORE} -> Java 17+ (records, sealed, switch expressions, streams)</li>
 *   <li>{@link #JAVA_OOP} -> SOLID, patrones de diseno, arquitectura hexagonal</li>
 *   <li>{@link #SQL} -> SQL, Oracle, indices, funciones de ventana</li>
 *   <li>{@link #SPRING_CORE} -> DI, transacciones, JPA/Hibernate, Spring Security</li>
 *   <li>{@link #SPRING_CLOUD} -> Eureka, Gateway, OpenFeign, Config Server, Resilience4j</li>
 *   <li>{@link #SOAP} -> APIs SOAP, WSDL, integracion con sistemas legacy</li>
 *   <li>{@link #TDD} -> ciclo Red-Green-Refactor, mocks, tests parametrizados</li>
 *   <li>{@link #DEVOPS} -> Docker, CI/CD, Linux, cloud</li>
 *   <li>{@link #DESIGN} -> diseno de sistemas, escalabilidad, trade-offs</li>
 * </ul>
 */
public enum Track {
	JAVA_CORE,
	JAVA_OOP,
	SQL,
	SPRING_CORE,
	SPRING_CLOUD,
	SOAP,
	TDD,
	DEVOPS,
	DESIGN
}
