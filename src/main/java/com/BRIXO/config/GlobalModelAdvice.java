package com.BRIXO.config;

import com.BRIXO.model.Usuario;
import com.BRIXO.repository.UsuarioRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalModelAdvice {

    private final UsuarioRepository usuarioRepository;

    public GlobalModelAdvice(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @ModelAttribute("contratistaAprobado")
    public boolean contratistaAprobado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return false;
        }
        Usuario usuario = usuarioRepository.findByEmail(auth.getName()).orElse(null);
        return usuario != null && usuario.isContratistaAprobado();
    }
}
