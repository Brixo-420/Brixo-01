package com.BRIXO.controller;

import com.BRIXO.service.NotificacionService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
@RequestMapping("/notificaciones")
public class NotificacionController {

    private final NotificacionService notificacionService;

    public NotificacionController(NotificacionService notificacionService) {
        this.notificacionService = notificacionService;
    }

    @GetMapping
    public String listar(Authentication authentication, Model model) {
        model.addAttribute("notificaciones", notificacionService.listarPorUsuario(authentication.getName()));
        return "notificaciones";
    }

    @PostMapping("/{id}/leida")
    @ResponseBody
    public Map<String, Object> marcarLeida(@PathVariable Long id, Authentication authentication) {
        notificacionService.marcarLeida(id, authentication.getName());
        return Map.of("ok", true);
    }

    @PostMapping("/marcar-todas")
    public String marcarTodas(Authentication authentication) {
        notificacionService.marcarTodasLeidas(authentication.getName());
        return "redirect:/notificaciones";
    }

    @GetMapping("/count")
    @ResponseBody
    public Map<String, Long> contarNoLeidas(Authentication authentication) {
        return Map.of("count", notificacionService.contarNoLeidas(authentication.getName()));
    }
}
