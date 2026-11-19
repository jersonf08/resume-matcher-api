# resume-matcher-api

API REST en Spring Boot que califica una hoja de vida frente a la descripción de un cargo usando un LLM (Claude, vía la Messages API de Anthropic).

`POST /api/evaluations` recibe `{"jobDescription": "...", "resume": "..."}` y devuelve `{"score": 0-100, "explanation": "2 o 3 frases"}`.

## Cómo correrlo

Requisitos: Java 21. Maven no hace falta (el proyecto trae el Maven wrapper).

```bash
export ANTHROPIC_API_KEY="sk-ant-..."   # PowerShell: $env:ANTHROPIC_API_KEY = "sk-ant-..."
./mvnw spring-boot:run                  # Windows: .\mvnw.cmd spring-boot:run
```

Pruebas (no requieren clave ni red; el LLM se simula):

```bash
./mvnw test
```

## Ejemplo

```bash
curl -X POST http://localhost:8080/api/evaluations \
  -H "Content-Type: application/json" \
  -d '{
        "jobDescription": "Desarrollador backend Java con Spring Boot, 3+ años, experiencia con APIs REST y MySQL.",
        "resume": "Ingeniero de sistemas. 5 años desarrollando APIs REST con Java 17 y Spring Boot. Bases de datos MySQL y PostgreSQL."
      }'
```

Respuesta (ilustrativa; el texto exacto lo genera el modelo):

```json
{"score": 88, "explanation": "El candidato cumple y supera los años de experiencia requeridos con Java y Spring Boot. También tiene experiencia en APIs REST y MySQL, que son los requisitos centrales del cargo."}
```

| Caso | Respuesta |
|---|---|
| Éxito | `200` con `score` y `explanation` |
| Falta un campo, viene vacío o el JSON es inválido | `400` `{"error": "El campo 'resume' es obligatorio y no puede estar vacío"}` |
| El LLM falla, rechaza la solicitud o responde algo inválido | `502` `{"error": "El servicio de evaluación no está disponible en este momento..."}` |

## Decisiones

- **`LlmClient` como interfaz** (`complete(systemPrompt, userPrompt)`), con `AnthropicLlmClient` como implementación. El servicio no conoce al proveedor y en las pruebas se reemplaza con un mock.
- **Sin SDK:** `RestClient` de Spring contra `POST /v1/messages`, con timeouts configurables. La clave se lee solo de `ANTHROPIC_API_KEY`. Nunca se registra en logs y `AnthropicProperties.toString()` la enmascara.
- **Modelo `claude-opus-5-5` con `effort: medium`.** Ambos son configurables en `application.yml`. Se activa el respaldo del servidor (`fallbacks: "default"`): si el modelo rechaza la solicitud por un falso positivo, Anthropic reintenta con otro modelo.
- **Prompt:**
  - Las instrucciones van en el `system` y los textos del usuario en el mensaje, delimitados con `<job_description>` y `<resume>`.
  - El modelo debe ignorar cualquier instrucción que venga dentro de esos textos y responder solo JSON.
  - Antes de enviarlos se escapan `<` y `>` en los textos, para que nadie pueda cerrar la etiqueta e inyectar instrucciones fuera de ella.
- **Parseo:**
  - Tolera texto o bloques de código alrededor del JSON: toma del primer `{` al último `}`.
  - Exige que `score` sea numérico y `explanation` un texto no vacío. Si no, responde 502.
  - El `score` se redondea y se ajusta al rango 0–100 en lugar de rechazarlo.
- **Errores:**
  - Un `@RestControllerAdvice` da un formato único, `{"error": ...}`.
  - El detalle de un fallo del LLM queda solo en el log del servidor; al cliente nunca le llegan la clave, códigos ni mensajes del proveedor.
  - Los errores propios de Spring (404, 405, 415) conservan su código.
- **Límite de 50.000 caracteres por campo,** para no enviar textos gigantes al modelo (costo y contexto).
- **Pruebas:**
  - `@WebMvcTest` con `LlmClient` simulado: éxito, falta `resume`, `jobDescription` vacío, JSON inválido, LLM que falla y LLM que responde basura.
  - Pruebas unitarias del parseo y del escape del prompt.
  - Una prueba de arranque del contexto completo.

**Mejoras posibles:**
- Usar *structured outputs* de Anthropic para garantizar el esquema JSON (hoy se valida al parsear).
- Reintentos con backoff ante 429/5xx.
- Limitar la tasa de solicitudes por cliente.
- Migrar a Spring Boot 4 (se usó 3.5.x por requisito de la prueba).

## Cómo usé Claude Code

- Le di a Claude Code los requisitos completos de la prueba y el diseño que quería (interfaz `LlmClient`, servicio, advice, pruebas). Le pedí que avanzara por pasos y explicara cada decisión antes de crear archivos.
- Generó el proyecto por capas: `pom.xml`, cliente de Anthropic, servicio y DTOs, controlador y manejo de errores, pruebas. Revisé las explicaciones de cada paso antes de continuar.
- Durante el proceso propuso detalles que no estaban en el enunciado y que acepté:
  - Escapar las etiquetas para evitar inyección de prompt.
  - Manejar el JSON mal formado con 400.
  - No convertir 404/405 en 500.
  - Enmascarar la clave en `toString()`.
  - Limitar el tamaño de los textos.
- Generó el Maven wrapper y corrió `./mvnw test` hasta verlo en verde (26 pruebas). Luego levantó la app y verificó con `curl` los casos 400, 502 y 405.
