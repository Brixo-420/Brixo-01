package com.BRIXO.service;

import com.BRIXO.dto.CotizacionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * LlmService – Wrapper para APIs de LLM (Groq / OpenAI / Anthropic).
 *
 * Configura mediante variables de entorno o application.properties:
 *   LLM_PROVIDER  = groq | openai | anthropic
 *   LLM_API_KEY   = tu clave de API
 *   LLM_MODEL     = (opcional, usa el modelo por defecto del proveedor)
 */
@Service
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);

    private static final String SYSTEM_PROMPT = """
        Eres un asistente experto en construcción, remodelación y servicios del hogar.
        Tu ÚNICA función es generar cotizaciones desglosadas a partir de la descripción
        que proporciona el usuario.

        REGLAS ESTRICTAS:
        1. Responde ÚNICAMENTE con un objeto JSON válido. Sin texto antes ni después.
        2. No uses bloques de código Markdown (```). Solo JSON puro.
        3. El JSON DEBE ajustarse exactamente al siguiente esquema:

        {
          "servicio_principal": "string – nombre corto del servicio",
          "materiales": [
            { "nombre": "string", "cantidad_estimada": "string con unidad" }
          ],
          "personal": [
            { "rol": "string – p.ej. Plomero, Albañil", "horas_estimadas": number }
          ],
          "complejidad": "bajo | medio | alto"
        }

        4. Si la descripción es ambigua, haz suposiciones razonables pero NO pidas más datos.
        5. Incluye al menos 1 material y 1 rol de personal.
        6. La complejidad debe ser exactamente uno de: "bajo", "medio" o "alto".
        """;

    @Value("${llm.provider:groq}")
    private String provider;

    @Value("${llm.api-key:}")
    private String apiKey;

    @Value("${llm.model:}")
    private String modelOverride;

    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public LlmService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restClient   = RestClient.create();
    }

    // ── Resultado tipado ──────────────────────────────────────────────────────

    public record LlmResult(boolean ok, CotizacionResult data, String error) {
        static LlmResult ok(CotizacionResult data) { return new LlmResult(true,  data, null); }
        static LlmResult error(String msg)          { return new LlmResult(false, null, msg);  }
    }

    // ── Método público ────────────────────────────────────────────────────────

    public LlmResult generarCotizacion(String descripcionUsuario) {
        if (apiKey == null || apiKey.isBlank()) {
            return LlmResult.error("No se ha configurado LLM_API_KEY en el servidor.");
        }

        descripcionUsuario = descripcionUsuario.trim();
        if (descripcionUsuario.isEmpty()) {
            return LlmResult.error("La descripción del servicio no puede estar vacía.");
        }

        String rawContent;
        try {
            rawContent = switch (provider) {
                case "openai"    -> callOpenAI(descripcionUsuario);
                case "anthropic" -> callAnthropic(descripcionUsuario);
                case "groq"      -> callGroq(descripcionUsuario);
                default -> throw new IllegalStateException("Proveedor LLM no soportado: " + provider);
            };
        } catch (RestClientResponseException ex) {
            String msg = extraerMensajeError(ex.getResponseBodyAsString());
            log.error("[LlmService] Error HTTP {}: {}", ex.getStatusCode(), msg);
            return LlmResult.error("Error de la API (" + ex.getStatusCode() + "): " + msg);
        } catch (Exception ex) {
            log.error("[LlmService] Error al llamar al LLM: {}", ex.getMessage());
            return LlmResult.error("Error de conexión con la IA: " + ex.getMessage());
        }

        rawContent = limpiarMarkdown(rawContent);

        CotizacionResult cotizacion;
        try {
            cotizacion = objectMapper.readValue(rawContent, CotizacionResult.class);
        } catch (Exception ex) {
            log.error("[LlmService] JSON inválido del LLM: {}", rawContent);
            return LlmResult.error("La IA no devolvió un JSON válido. Intenta reformular tu solicitud.");
        }

        String validationError = validarEsquema(cotizacion);
        if (validationError != null) {
            log.error("[LlmService] Esquema inválido: {}", validationError);
            return LlmResult.error("Formato de respuesta incorrecto: " + validationError);
        }

        return LlmResult.ok(cotizacion);
    }

    // ── Llamadas a APIs ───────────────────────────────────────────────────────

    private String callAnthropic(String userMessage) {
        String model = modelOverride.isBlank() ? "claude-sonnet-4-20250514" : modelOverride;

        Map<String, Object> payload = Map.of(
            "model",      model,
            "max_tokens", 1024,
            "system",     SYSTEM_PROMPT,
            "messages",   List.of(Map.of("role", "user", "content", userMessage))
        );

        JsonNode response = restClient.post()
            .uri("https://api.anthropic.com/v1/messages")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .body(payload)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (req, res) -> {
                throw new RestClientResponseException(
                    "Error Anthropic", res.getStatusCode(),
                    res.getStatusText(), res.getHeaders(),
                    res.getBody().readAllBytes(), null
                );
            })
            .body(JsonNode.class);

        return response.at("/content/0/text").asText();
    }

    private String callOpenAI(String userMessage) {
        String model = modelOverride.isBlank() ? "gpt-4o-mini" : modelOverride;

        Map<String, Object> payload = Map.of(
            "model",       model,
            "max_tokens",  1024,
            "temperature", 0.3,
            "messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user",   "content", userMessage)
            )
        );

        JsonNode response = restClient.post()
            .uri("https://api.openai.com/v1/chat/completions")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + apiKey)
            .body(payload)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (req, res) -> {
                throw new RestClientResponseException(
                    "Error OpenAI", res.getStatusCode(),
                    res.getStatusText(), res.getHeaders(),
                    res.getBody().readAllBytes(), null
                );
            })
            .body(JsonNode.class);

        return response.at("/choices/0/message/content").asText();
    }

    private String callGroq(String userMessage) {
        String model = modelOverride.isBlank() ? "llama-3.3-70b-versatile" : modelOverride;

        Map<String, Object> payload = Map.of(
            "model",           model,
            "max_tokens",      1024,
            "temperature",     0.3,
            "response_format", Map.of("type", "json_object"),
            "messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user",   "content", userMessage)
            )
        );

        JsonNode response = restClient.post()
            .uri("https://api.groq.com/openai/v1/chat/completions")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + apiKey)
            .body(payload)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (req, res) -> {
                throw new RestClientResponseException(
                    "Error Groq", res.getStatusCode(),
                    res.getStatusText(), res.getHeaders(),
                    res.getBody().readAllBytes(), null
                );
            })
            .body(JsonNode.class);

        return response.at("/choices/0/message/content").asText();
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private String limpiarMarkdown(String text) {
        text = text.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```(?:json)?\\s*", "");
            text = text.replaceFirst("\\s*```$", "");
        }
        return text.trim();
    }

    private String validarEsquema(CotizacionResult c) {
        if (c.getServicioPrincipal() == null || c.getServicioPrincipal().isBlank())
            return "Falta \"servicio_principal\" (string).";

        if (c.getMateriales() == null || c.getMateriales().isEmpty())
            return "Falta \"materiales\" (array no vacío).";
        for (int i = 0; i < c.getMateriales().size(); i++) {
            var m = c.getMateriales().get(i);
            if (m.getNombre() == null || m.getNombre().isBlank())
                return "materiales[" + i + "] no tiene \"nombre\".";
            if (m.getCantidadEstimada() == null)
                return "materiales[" + i + "] no tiene \"cantidad_estimada\".";
        }

        if (c.getPersonal() == null || c.getPersonal().isEmpty())
            return "Falta \"personal\" (array no vacío).";
        for (int i = 0; i < c.getPersonal().size(); i++) {
            var p = c.getPersonal().get(i);
            if (p.getRol() == null || p.getRol().isBlank())
                return "personal[" + i + "] no tiene \"rol\".";
            if (p.getHorasEstimadas() == null)
                return "personal[" + i + "] no tiene \"horas_estimadas\".";
        }

        if (!List.of("bajo", "medio", "alto").contains(c.getComplejidad()))
            return "\"complejidad\" debe ser \"bajo\", \"medio\" o \"alto\".";

        return null;
    }

    private String extraerMensajeError(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            JsonNode msg  = node.at("/error/message");
            if (!msg.isMissingNode()) return msg.asText();
            JsonNode type = node.at("/error/type");
            if (!type.isMissingNode()) return type.asText();
        } catch (Exception ignored) {}
        return body;
    }
}
