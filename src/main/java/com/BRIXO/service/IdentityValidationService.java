package com.BRIXO.service;

import com.BRIXO.model.Usuario;
import com.BRIXO.model.ValidacionIdentidad;
import com.BRIXO.repository.ValidacionIdentidadRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;

@Service
public class IdentityValidationService {

    private static final Logger log = LoggerFactory.getLogger(IdentityValidationService.class);

    private final ValidacionIdentidadRepository validacionRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    @Value("${python.service.url:http://localhost:8001}")
    private String pythonServiceUrl;

    private final R2FileService r2FileService;

    public IdentityValidationService(ValidacionIdentidadRepository validacionRepo,
                                     R2FileService r2FileService) {
        this.validacionRepo = validacionRepo;
        this.r2FileService = r2FileService;
    }

    @SuppressWarnings("unchecked")
    public ValidacionIdentidad validar(Usuario usuario,
                                       MultipartFile documento,
                                       MultipartFile selfie) throws IOException {

        byte[] docBytes    = documento.getBytes();
        byte[] selfieBytes = selfie.getBytes();

        String docKey    = r2FileService.upload(docBytes,    documento.getOriginalFilename(), "doc");
        String selfieKey = r2FileService.upload(selfieBytes, selfie.getOriginalFilename(),    "selfie");

        ValidacionIdentidad validacion = validacionRepo.findByUsuarioId(usuario.getId())
                .orElseGet(ValidacionIdentidad::new);

        validacion.setUsuario(usuario);
        validacion.setDocumentoPath(docKey);
        validacion.setSelfiePath(selfieKey);
        validacion.setFechaValidacion(LocalDateTime.now());
        validacion.setIntentos(validacion.getIntentos() + 1);

        try {
            String docB64    = Base64.getEncoder().encodeToString(docBytes);
            String selfieB64 = Base64.getEncoder().encodeToString(selfieBytes);
            String jsonBody  = "{\"documento_b64\":\"" + docB64 + "\",\"selfie_b64\":\"" + selfieB64 + "\"}";

            log.info("Enviando a Python — doc={} bytes, selfie={} bytes, JSON={} chars",
                    docBytes.length, selfieBytes.length, jsonBody.length());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(pythonServiceUrl + "/api/validar"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("Python respondió [{}]: {}",
                    response.statusCode(),
                    response.body().substring(0, Math.min(200, response.body().length())));

            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);

            Boolean match    = (Boolean) result.get("match");
            Object  scoreObj = result.get("score");
            String  error    = (String)  result.get("error");

            validacion.setVerificado(Boolean.TRUE.equals(match));
            validacion.setScore(scoreObj != null ? ((Number) scoreObj).doubleValue() : null);
            validacion.setError(error);

        } catch (Exception e) {
            log.error("Error llamando servicio Python: {}", e.getMessage(), e);
            validacion.setVerificado(false);
            validacion.setError("Servicio de validación no disponible: " + e.getMessage());
        }

        return validacionRepo.save(validacion);
    }
}
