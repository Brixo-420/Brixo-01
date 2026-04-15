package com.BRIXO.controller;

import com.BRIXO.service.NotificacionService;
import com.BRIXO.service.SolicitudContratistaService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.view.RedirectView;

@Controller
public class HomeController {

    private final SolicitudContratistaService solicitudService;
    private final NotificacionService notificacionService;

    public HomeController(SolicitudContratistaService solicitudService, NotificacionService notificacionService) {
        this.solicitudService = solicitudService;
        this.notificacionService = notificacionService;
    }

    @GetMapping({"/", "/home"})
    public String home() {
        return "home";
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        if (authentication != null) {
            String email = authentication.getName();
            boolean esCliente = authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_CLIENTE"));
            boolean esAdmin = authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));

            model.addAttribute("notificacionesNoLeidas", notificacionService.contarNoLeidas(email));

            if (esCliente) {
                model.addAttribute("solicitudPendiente", solicitudService.tieneSolicitudPendiente(email));
            }
            if (esAdmin) {
                model.addAttribute("solicitudesPendientes", solicitudService.contarPendientes());
            }
        }
        return "dashboard";
    }

    @GetMapping("/admin")
    public RedirectView adminHome() {
        return new RedirectView("/admin/usuarios");
    }
}
