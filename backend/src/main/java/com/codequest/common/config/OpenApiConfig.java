package com.codequest.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documentacion OpenAPI expuesta en /swagger-ui.html. */
@Configuration
public class OpenApiConfig {

	@Bean
	OpenAPI codeQuestOpenApi() {
		return new OpenAPI().info(new Info()
				.title("CodeQuest API")
				.version("0.1.0")
				.description("""
						API de la plataforma de practica de codigo.
						Cubre catalogo de retos, motor de ejecucion de codigo aislado,
						banco de preguntas conceptuales, ejercicios de diseno de sistemas
						y seguimiento de progreso.
						"""));
	}
}
