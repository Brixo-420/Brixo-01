package com.BRIXO.controller;

import com.BRIXO.service.CotizacionService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
@RequestMapping("/servicios")
public class CotizacionController {

    private final CotizacionService cotizacionService;

    public CotizacionController(CotizacionService cotizacionService) {
        this.cotizacionService = cotizacionService;
    }

    @PostMapping("/{id}/cotizar")
    public String cotizar(@PathVariable Long id,
                          @RequestParam BigDecimal monto,
                          @RequestParam(required = false) String mensaje,
                          Authentication authentication,
                          RedirectAttributes redirectAttributes) {
        try {
            cotizacionService.crearCotizacion(id, authentication.getName(), monto, mensaje);
            redirectAttributes.addFlashAttribute("ok", "Cotización enviada correctamente");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/servicios/" + id + "/detalle";
    }

    @PostMapping("/cotizaciones/{cotizacionId}/aceptar")
    public String aceptar(@PathVariable Long cotizacionId,
                          @RequestParam Long servicioId,
                          Authentication authentication,
                          RedirectAttributes redirectAttributes) {
        try {
            cotizacionService.aceptarCotizacion(cotizacionId, authentication.getName());
            redirectAttributes.addFlashAttribute("ok", "Cotización aceptada. El contratista ha sido asignado.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/servicios/" + servicioId + "/detalle";
    }

    @PostMapping("/cotizaciones/{cotizacionId}/rechazar")
    public String rechazar(@PathVariable Long cotizacionId,
                           @RequestParam Long servicioId,
                           Authentication authentication,
                           RedirectAttributes redirectAttributes) {
        try {
            cotizacionService.rechazarCotizacion(cotizacionId, authentication.getName());
            redirectAttributes.addFlashAttribute("ok", "Cotización rechazada");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/servicios/" + servicioId + "/detalle";
    }
}
