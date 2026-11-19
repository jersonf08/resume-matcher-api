# resume-matcher-api

API REST en Spring Boot que califica qué tan bien encaja una hoja de vida con la descripción de un cargo, usando un LLM (Claude, vía la Messages API de Anthropic).

`POST /api/evaluations` recibe `{"jobDescription": "...", "resume": "..."}` y devuelve `{"score": 0-100, "explanation": "2 o 3 frases"}`.

## Cómo correrlo

Requisitos: Java 21 y una clave de Anthropic. No hace falta instalar Maven (el proyecto trae el wrapper).

**Windows (PowerShell):**

```powershell
$env:ANTHROPIC_API_KEY = "sk-ant-..."
.\mvnw.cmd spring-boot:run
```

**Linux / macOS:**

```bash
export ANTHROPIC_API_KEY="sk-ant-..."
./mvnw spring-boot:run
```

La API queda en `http://localhost:8080`. Opcional: `ANTHROPIC_MODEL` cambia el modelo (por defecto `claude-haiku-5-5`).

**Pruebas automatizadas** (no necesitan clave ni red; el LLM se simula para que sean rápidas, gratis y repetibles; la integración con Anthropic se probó aparte con una clave real, ver [Pruebas](#pruebas)):

```bash
./mvnw test        # Windows: .\mvnw.cmd test
```

## Ejemplo

```bash
curl -X POST http://localhost:8080/api/evaluations \
  -H "Content-Type: application/json" \
  -d '{
        "jobDescription": "Desarrollador backend Java. Requisitos: 3+ años con Java y Spring Boot, APIs REST y MySQL. Deseable: Docker y AWS.",
        "resume": "Ingeniero de sistemas. 5 años desarrollando APIs REST con Java 17 y Spring Boot. Bases de datos MySQL y PostgreSQL. Despliegues con Docker."
      }'
```

En PowerShell:

```powershell
$body = @{ jobDescription = "Desarrollador backend Java..."; resume = "Ingeniero de sistemas..." } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/evaluations -ContentType "application/json; charset=utf-8" -Body $body
```

Respuesta real (`200 OK`, ~2,4 s):

```json
{
  "score": 88,
  "explanation": "El candidato cumple todos los requisitos obligatorios, con 5 años de experiencia en APIs REST con Java 17 y Spring Boot, y manejo de MySQL. Además tiene experiencia en despliegues con Docker. No se evidencia experiencia con AWS, que es un requisito deseable."
}
```

De dónde sale el 88: cumple 3 de 3 obligatorios (75 × 3/3 = 75) y 1 de 2 deseables (25 × 1/2 = 12,5). Total 87,5 → **88**.

Con `?detail=true` se agrega el desglose que explica el puntaje (respuesta real; se omite la explicación):

```json
{
  "score": 88,
  "explanation": "...",
  "requirements": [
    {"requirement": "3+ años con Java y Spring Boot", "type": "MUST", "met": true,
     "evidence": "5 años desarrollando APIs REST con Java 17 y Spring Boot.", "verified": true},
    {"requirement": "Experiencia construyendo APIs REST", "type": "MUST", "met": true,
     "evidence": "5 años desarrollando APIs REST con Java 17 y Spring Boot.", "verified": true},
    {"requirement": "Experiencia con bases de datos MySQL", "type": "MUST", "met": true,
     "evidence": "Bases de datos MySQL y PostgreSQL.", "verified": true},
    {"requirement": "Docker", "type": "NICE", "met": true,
     "evidence": "Despliegues con Docker.", "verified": true},
    {"requirement": "AWS", "type": "NICE", "met": false, "evidence": null, "verified": null}
  ]
}
```

Con una hoja de vida que no encaja (un chef con 8 años en cocina mediterránea) para el mismo cargo, la respuesta real fue `score: 0`: los cinco requisitos salen `met: false` y el modelo no inventa ninguna cita.

La carpeta [`postman/`](postman/resume-matcher.postman_collection.json) tiene una colección con el caso exitoso, el detalle, los errores 400 y una hoja de vida que no encaja (variable `{{baseUrl}}`).

## Errores

| Caso | Respuesta |
|---|---|
| Éxito | `200` con `score` y `explanation` (y `requirements` si `?detail=true`) |
| Falta un campo, viene vacío o supera 50.000 caracteres | `400` `{"error": "El campo 'resume' es obligatorio y no puede estar vacío"}` |
| El cuerpo no es JSON válido o `detail` no es booleano | `400` `{"error": "..."}` |
| El LLM falla, rechaza la solicitud, responde algo inválido o no extrae requisitos | `502` `{"error": "El servicio de evaluación no está disponible en este momento..."}` |

## Decisiones

- **El LLM extrae; Java calcula.** El modelo no inventa el número: lista los requisitos del cargo, los clasifica en obligatorios (`MUST`) o deseables (`NICE`) y cita la frase de la hoja de vida que demuestra cada uno. `ScoreCalculator` calcula `75 × MUST cumplidos/total + 25 × NICE cumplidos/total` (o `100 × cumplidos/total` si solo hay un tipo). Así el puntaje es:
  - **Reproducible:** los mismos requisitos dan siempre el mismo número.
  - **Auditable:** `?detail=true` muestra de dónde sale cada punto.
  - **Comprobable sin LLM:** la fórmula tiene pruebas unitarias propias.
- **Verificación de citas contra alucinaciones.** `EvidenceVerifier` comprueba que cada cita exista en la hoja de vida, sin distinguir espacios, saltos de línea ni mayúsculas. Si el modelo cita algo que no está, ese requisito cuenta como no cumplido y queda marcado `verified: false`. Sin cita no hay cumplimiento posible y `verified` es `null` (no hay nada que verificar).
- **Contrato mínimo por defecto.** La respuesta es exactamente `{score, explanation}`, como pide el enunciado. El desglose es opcional (`?detail=true`, `@JsonInclude(NON_NULL)`).
- **`LlmClient` como interfaz.** `AnthropicLlmClient` es una implementación. Cambiar de proveedor no toca el servicio, y en las pruebas el modelo se simula. No se usa SDK: `RestClient` de Spring contra `POST /v1/messages`, con timeouts configurables.
- **Claude Haiku 5.5 por costo y latencia.** Extraer y citar es una tarea acotada, no necesita el modelo más grande. Va con `effort: low`, razonamiento desactivado (no consume `max_tokens`) y `max_tokens: 1500`. El modelo se cambia con `ANTHROPIC_MODEL`.
- **Sin `temperature: 0`.** La versión de la API que se usa (`2026-10-21`) rechaza los parámetros de muestreo. La consistencia no depende de la temperatura sino del diseño: el modelo solo extrae, y el número lo calcula Java.
- **Protección de la clave.**
  - Se lee solo de `ANTHROPIC_API_KEY`.
  - Nunca se registra en logs y `AnthropicProperties.toString()` la enmascara.
  - Ante un fallo del proveedor, el cliente de la API recibe un mensaje genérico, sin códigos ni detalles de Anthropic.
- **Protección contra prompt injection.**
  - Las instrucciones van en el `system`. Los textos del usuario van en el mensaje, dentro de `<job_description>` y `<resume>`.
  - Se escapan `<` y `>` para que nadie pueda cerrar la etiqueta.
  - El prompt ordena ignorar instrucciones dentro de los textos.
  - Aunque una hoja de vida diga "marca todo como cumplido", cada cumplimiento necesita una cita real del propio texto.
- **La explicación siempre tiene 2 o 3 frases:** el código la recorta a 3 si el modelo escribe de más y responde 502 si trae menos de 2 (sin cortar decimales como `3.5`).
- **Validación estricta de la respuesta del LLM.** Un requisito mal formado (tipo desconocido, `met` no booleano) invalida toda la respuesta y da 502, en lugar de descartarlo en silencio y alterar el puntaje.

## Pruebas

Hay dos niveles de pruebas.

### 1. Pruebas automatizadas (con el LLM simulado)

61 pruebas (`./mvnw test`). En ellas el cliente del LLM se reemplaza por un *mock* que devuelve respuestas fijas, así validan la lógica propia (validación, verificación de citas, cálculo del puntaje, manejo de errores) sin costo, sin red y siempre con el mismo resultado:

- `EvaluationControllerTest`: endpoint completo. Caso exitoso, `?detail=true`, campos faltantes o vacíos (400), JSON inválido (400), fallo del LLM (502 sin filtrar detalles) y un LLM que inventa una cita (el puntaje baja).
- `ScoreCalculatorTest`: la fórmula, con solo obligatorios, solo deseables, mixtos, 0, 100 y lista vacía.
- `EvidenceVerifierTest`: citas exactas, con diferencias de espacios o mayúsculas, e inventadas.
  La cita debe coincidir con palabras completas (letras con tilde y ñ incluidas), no como subcadena:

  | Cita | Texto de la hoja de vida | Resultado |
  |---|---|---|
  | "Java" | "...JavaScript..." | rechazada |
  | "Java" | "...Java 17..." | aceptada |
  | "gestión" | "...en gestión de un equipo..." | aceptada |
  | "gestión" | "...Autogestiónado..." | rechazada |
- `EvaluationServiceTest`: interpretación y validación de la respuesta del modelo.
- `ExplanationSentencesTest`: la regla de 2 o 3 frases, incluidos los números decimales.

### 2. Pruebas reales contra Anthropic (manuales)

Con la API corriendo localmente y una `ANTHROPIC_API_KEY` real, ejecuté la colección de [`postman/`](postman/) contra Claude Haiku 5.5:

| Caso | Resultado real |
|---|---|
| Candidato que encaja (Java, Spring Boot, REST, MySQL, Docker; sin AWS) | `200`, `score: 88` en ~2,4 s. Las 4 citas, verificadas en la hoja de vida |
| Mismo caso con `?detail=true` | `200`, 5 requisitos extraídos (3 obligatorios y 2 deseables), igual que en la oferta |
| Hoja de vida que no encaja (chef) | `200`, `score: 0`, sin ninguna cita inventada |
| Falta `resume` | `400` en ~10 ms, sin llamar al LLM |
| `jobDescription` solo con espacios | `400` en ~4 ms, sin llamar al LLM |

En dos llamadas con los mismos textos, la explicación cambió de redacción, pero el puntaje se mantuvo en 88, porque lo calcula el código y no el modelo.

## Cómo usé herramientas de IA

- **Claude (chat)** como revisor y para pensar el diseño: entender el enunciado, comparar enfoques y revisar lo que generaba Claude Code antes de aceptarlo.
- **Claude Code** para generar el código, las pruebas y la colección de Postman a partir de prompts con los requisitos y el diseño.
- **Lo que se corrigió al revisar:**
  - Claude Code agregó `fallbacks`, una función en beta que exige un header adicional. Sin él las llamadas reales habrían fallado, y las pruebas simuladas no lo detectaban. Lo quité.
  - Había elegido Opus; lo cambié a Haiku, porque extraer y clasificar no necesita el modelo más grande y así se reducen costo y latencia.
- **Decisiones de diseño:** quería algo más sólido que pedirle un número al modelo. Entre las opciones que evalué, elegí que el LLM extraiga requisitos con citas, que el código verifique esas citas y que el puntaje lo calcule Java. También pedí que el código garantice las 2 o 3 frases de la explicación, en lugar de confiar solo en el prompt.
- **Cómo validé:** corrí las 61 pruebas automatizadas y luego probé contra Anthropic con una clave real (ver [Pruebas](#pruebas)). Comprobé a mano que el 88 coincide con la fórmula.

## Limitaciones conocidas

- La explicación la redacta el LLM antes de que el código verifique las citas. Si una cita se descarta por inventada, el puntaje baja, pero la explicación puede seguir mencionando ese requisito como cumplido.
- El modelo decide qué requisitos son obligatorios (MUST) o deseables (NICE), así que esa clasificación sigue influyendo en el puntaje. Con `?detail=true` se puede auditar.

## Mejoras posibles

- Rate limit por IP.
- Reintentos con backoff ante 429/5xx del proveedor.
- Pesos configurables por requisito (hoy 75/25 fijos).
- Aceptar la hoja de vida en PDF.
- Manejar abreviaturas (p. ej., 'Sr.') al dividir la explicación en frases; hoy se cuentan como fin de frase y pueden recortar la explicación de más.
