package com.BRIXO.config;

import com.BRIXO.model.Usuario;
import com.BRIXO.repository.UsuarioRepository;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Atributos que necesitan las plantillas compartidas (navbar, sidebar) y que por
 * lo tanto hacen falta en casi todas las vistas, no en un controlador puntual.
 */
@ControllerAdvice
public class GlobalModelAttributes {

    private final UsuarioRepository usuarioRepository;

    public GlobalModelAttributes(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * El navbar y el dashboard muestran el boton de cambiar rol solo si el usuario
     * es un contratista aprobado. Se consulta en vivo (y no desde las authorities
     * de la sesion) para que el boton aparezca apenas el admin aprueba la
     * solicitud, sin obligar al usuario a volver a iniciar sesion.
     */
    @ModelAttribute("contratistaAprobado")
    public boolean contratistaAprobado(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return usuarioRepository.findByEmail(authentication.getName())
                .map(Usuario::isContratistaAprobado)
                .orElse(false);
    }
}
