package com.BRIXO.controller;

import com.BRIXO.model.Usuario;
import com.BRIXO.service.ResenaService;
import com.BRIXO.service.UsuarioService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Controller
@RequestMapping("/perfil")
public class PerfilController {

    private final UsuarioService usuarioService;
    private final ResenaService resenaService;

    public PerfilController(UsuarioService usuarioService, ResenaService resenaService) {
        this.usuarioService = usuarioService;
        this.resenaService = resenaService;
    }

    @GetMapping
    public String verPerfil(Authentication authentication, Model model) {
        Usuario usuario = usuarioService.buscarPorEmail(authentication.getName());
        model.addAttribute("usuario", usuario);
        model.addAttribute("resumen", resenaService.resumen(usuario.getId()));
        return "perfil";
    }

    @PostMapping
    public String actualizarPerfil(
            @RequestParam String nombre,
            @RequestParam String email,
            @RequestParam(required = false) String telefono,
            @RequestParam(required = false) String direccion,
            @RequestParam(required = false) String ciudad,
            @RequestParam(required = false) String password,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            String emailActual = authentication.getName();
            Usuario actualizado = usuarioService.actualizarPerfil(emailActual, nombre, email, telefono, direccion, ciudad, password);

            // Si cambió el email, actualizar el SecurityContext
            if (!emailActual.equals(actualizado.getEmail())) {
                Authentication newAuth = new UsernamePasswordAuthenticationToken(
                        actualizado.getEmail(),
                        authentication.getCredentials(),
                        authentication.getAuthorities()
                );
                SecurityContextHolder.getContext().setAuthentication(newAuth);
            }

            redirectAttributes.addFlashAttribute("ok", "Perfil actualizado correctamente");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/perfil";
    }

    @PostMapping("/eliminar")
    public String eliminarPerfil(
            @RequestParam String passwordConfirm,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes
    ) {
        try {
            usuarioService.eliminarPerfil(authentication.getName(), passwordConfirm);
            SecurityContextHolder.clearContext();
            request.getSession().invalidate();
            return "redirect:/login?eliminado";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/perfil";
        }
    }
}
