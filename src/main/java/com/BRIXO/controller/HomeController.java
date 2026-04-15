package com.BRIXO.controller;

import com.BRIXO.model.Rol;
import com.BRIXO.model.Usuario;
import com.BRIXO.repository.RolRepository;
import com.BRIXO.repository.UsuarioRepository;
import com.BRIXO.service.NotificacionService;
import com.BRIXO.service.SolicitudContratistaService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

import java.util.List;

@Controller
public class HomeController {

    private final SolicitudContratistaService solicitudService;
    private final NotificacionService notificacionService;
    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;

    public HomeController(SolicitudContratistaService solicitudService, NotificacionService notificacionService,
                          UsuarioRepository usuarioRepository, RolRepository rolRepository) {
        this.solicitudService = solicitudService;
        this.notificacionService = notificacionService;
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
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

            Usuario usuario = usuarioRepository.findByEmail(email).orElse(null);
            if (usuario != null) {
                model.addAttribute("contratistaAprobado", usuario.isContratistaAprobado());
            }

            if (esCliente) {
                model.addAttribute("solicitudPendiente", solicitudService.tieneSolicitudPendiente(email));
            }
            if (esAdmin) {
                model.addAttribute("solicitudesPendientes", solicitudService.contarPendientes());
            }
        }
        return "dashboard";
    }

    @PostMapping("/cambiar-rol")
    public String cambiarRol(Authentication authentication, RedirectAttributes redirectAttributes) {
        if (authentication == null) {
            return "redirect:/login";
        }

        String email = authentication.getName();
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (!usuario.isContratistaAprobado()) {
            redirectAttributes.addFlashAttribute("error", "No tienes permiso para cambiar de rol");
            return "redirect:/dashboard";
        }

        String rolActual = usuario.getRol() != null ? usuario.getRol().getNombre() : "CLIENTE";
        String nuevoRolNombre = "CONTRATISTA".equalsIgnoreCase(rolActual) ? "CLIENTE" : "CONTRATISTA";

        Rol nuevoRol = rolRepository.findByNombre(nuevoRolNombre)
                .orElseThrow(() -> new IllegalArgumentException("Rol no encontrado"));

        usuario.setRol(nuevoRol);
        usuarioRepository.save(usuario);

        // Actualizar la sesión de seguridad con el nuevo rol
        UsernamePasswordAuthenticationToken newAuth = new UsernamePasswordAuthenticationToken(
                authentication.getPrincipal(),
                authentication.getCredentials(),
                List.of(new SimpleGrantedAuthority("ROLE_" + nuevoRolNombre))
        );
        SecurityContextHolder.getContext().setAuthentication(newAuth);

        redirectAttributes.addFlashAttribute("ok", "Ahora estás como " + nuevoRolNombre);
        return "redirect:/dashboard";
    }

    @GetMapping("/admin")
    public RedirectView adminHome() {
        return new RedirectView("/admin/usuarios");
    }
}
