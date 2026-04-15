package com.BRIXO.controller;

import com.BRIXO.service.SolicitudContratistaService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/solicitudes")
public class AdminSolicitudController {

    private final SolicitudContratistaService solicitudService;

    public AdminSolicitudController(SolicitudContratistaService solicitudService) {
        this.solicitudService = solicitudService;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("solicitudes", solicitudService.listarTodas());
        model.addAttribute("pendientes", solicitudService.contarPendientes());
        return "admin/solicitudes";
    }

    @PostMapping("/{id}/aprobar")
    public String aprobar(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        solicitudService.aprobar(id);
        redirectAttributes.addFlashAttribute("ok", "Solicitud aprobada. El usuario ahora es contratista.");
        return "redirect:/admin/solicitudes";
    }

    @PostMapping("/{id}/rechazar")
    public String rechazar(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        solicitudService.rechazar(id);
        redirectAttributes.addFlashAttribute("ok", "Solicitud rechazada.");
        return "redirect:/admin/solicitudes";
    }
}
