package com.BRIXO.controller;

import com.BRIXO.repository.UsuarioRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.view.RedirectView;

@Controller
public class HomeController {

    private final UsuarioRepository usuarioRepository;

    public HomeController(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping({"/", "/home"})
    public String home() {
        return "home";
    }

    @GetMapping("/dashboard")
    public String dashboard() {
        return "dashboard";
    }

    @GetMapping("/admin")
    public RedirectView adminHome() {
        return new RedirectView("/admin/usuarios");
    }

    /** GET /map – Mapa de profesionales con Leaflet */
    @GetMapping("/map")
    public String map(Model model) {
        model.addAttribute("contratistas",
                usuarioRepository.findByRolNombre("CONTRATISTA"));
        return "map";
    }
}
