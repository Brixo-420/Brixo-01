package com.BRIXO.controller;

import com.BRIXO.model.Usuario;
import com.BRIXO.service.LoginAttemptService;
import com.BRIXO.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.security.web.WebAttributes;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private final UsuarioService usuarioService;
    private final LoginAttemptService loginAttemptService;

    public AuthController(UsuarioService usuarioService, LoginAttemptService loginAttemptService) {
        this.usuarioService = usuarioService;
        this.loginAttemptService = loginAttemptService;
    }

    @GetMapping("/login")
    public String login(HttpServletRequest request, Model model) {
        // Extraer mensaje de error de Spring Security
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object ex = session.getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
            if (ex instanceof org.springframework.security.authentication.LockedException locked) {
                model.addAttribute("bloqueado", true);
                model.addAttribute("mensajeBloqueo", locked.getMessage());
            }
        }
        return "login";
    }

    @GetMapping("/registro")
    public String registroForm(Model model) {
        model.addAttribute("usuario", new Usuario());
        return "registro";
    }

    @PostMapping("/registro")
    public String registrar(
            @Valid @ModelAttribute("usuario") Usuario usuario,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            return "registro";
        }
        try {
            usuarioService.registrar(usuario);
            redirectAttributes.addFlashAttribute("registroExitoso", "Cuenta creada exitosamente. Inicia sesión.");
            return "redirect:/login";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            return "registro";
        }
    }
}
