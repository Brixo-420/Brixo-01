package com.BRIXO.service;

import com.BRIXO.model.Usuario;
import com.BRIXO.repository.UsuarioRepository;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;
    private final LoginAttemptService loginAttemptService;

    public CustomUserDetailsService(UsuarioRepository usuarioRepository, LoginAttemptService loginAttemptService) {
        this.usuarioRepository = usuarioRepository;
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (loginAttemptService.isBlocked(username)) {
            int mins = loginAttemptService.getLockMinutesRemaining(username);
            throw new LockedException("Cuenta bloqueada por demasiados intentos. Intenta en " + mins + " minutos.");
        }

        Usuario usuario = usuarioRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        String nombreRol = usuario.getRol() != null ? usuario.getRol().getNombre() : "CLIENTE";
        String rolNormalizado = nombreRol == null ? "CLIENTE" : nombreRol.trim().toUpperCase();
        if (rolNormalizado.startsWith("ROLE_")) {
            rolNormalizado = rolNormalizado.substring(5);
        }

        return new User(
                usuario.getEmail(),
                usuario.getPassword(),
            true,
                true,
                true,
                true,
            java.util.List.of(new SimpleGrantedAuthority("ROLE_" + rolNormalizado))
        );
    }
}
