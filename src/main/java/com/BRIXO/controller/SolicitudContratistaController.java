package com.BRIXO.controller;

import com.BRIXO.service.SolicitudContratistaService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class SolicitudContratistaController {

    private final SolicitudContratistaService solicitudService;

    public SolicitudContratistaController(SolicitudContratistaService solicitudService) {
        this.solicitudService = solicitudService;
    }

    @PostMapping("/solicitar-contratista")
    public String solicitarContratista(Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            solicitudService.crearSolicitud(authentication.getName());
            redirectAttributes.addFlashAttribute("ok", "Solicitud enviada. El administrador revisará tu petición.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/dashboard";
    }
}
