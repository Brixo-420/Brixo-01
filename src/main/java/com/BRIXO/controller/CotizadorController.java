package com.BRIXO.controller;

import com.BRIXO.service.LlmService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
@RequestMapping("/cotizador")
public class CotizadorController {

    private final LlmService llmService;

    public CotizadorController(LlmService llmService) {
        this.llmService = llmService;
    }

    /** GET /cotizador – Muestra el formulario del Cotizador IA */
    @GetMapping
    public String index(Model model, Authentication auth) {
        boolean loggedIn = auth != null && auth.isAuthenticated()
                && auth.getAuthorities().stream()
                       .noneMatch(a -> a.getAuthority().equals("ROLE_ANONYMOUS"));
        model.addAttribute("loggedIn", loggedIn);
        return "cotizador";
    }

    /**
     * POST /cotizador/generar – Llama al LLM y devuelve JSON.
     * Soporta AJAX (X-Requested-With: XMLHttpRequest).
     */
    @PostMapping("/generar")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generar(@RequestParam String descripcion) {

        if (descripcion == null || descripcion.trim().length() < 10) {
            return ResponseEntity.ok(Map.of("ok", false,
                    "error", "La descripción debe tener al menos 10 caracteres."));
        }
        if (descripcion.length() > 2000) {
            return ResponseEntity.ok(Map.of("ok", false,
                    "error", "La descripción es demasiado larga (máximo 2000 caracteres)."));
        }

        LlmService.LlmResult resultado = llmService.generarCotizacion(descripcion.trim());

        if (!resultado.ok()) {
            return ResponseEntity.ok(Map.of("ok", false, "error", resultado.error()));
        }

        return ResponseEntity.ok(Map.of("ok", true, "data", resultado.data()));
    }
}
