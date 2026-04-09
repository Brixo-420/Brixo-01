package com.BRIXO.config;

import com.BRIXO.model.Rol;
import com.BRIXO.model.Usuario;
import com.BRIXO.repository.RolRepository;
import com.BRIXO.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final RolRepository rolRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(RolRepository rolRepository, UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.rolRepository = rolRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        rolRepository.findByNombre("CLIENTE")
            .orElseGet(() -> rolRepository.save(new Rol("CLIENTE")));
        rolRepository.findByNombre("CONTRATISTA")
            .orElseGet(() -> rolRepository.save(new Rol("CONTRATISTA")));
        Rol rolAdmin = rolRepository.findByNombre("ADMIN")
            .orElseGet(() -> rolRepository.save(new Rol("ADMIN")));

        usuarioRepository.findAll().forEach(usuario -> {
            String password = usuario.getPassword();
            if (password != null && !password.startsWith("$2a$") && !password.startsWith("$2b$") && !password.startsWith("$2y$")) {
            usuario.setPassword(passwordEncoder.encode(password));
            usuarioRepository.save(usuario);
            }
        });

        if (usuarioRepository.findByEmail("admin@brixo.com").isEmpty()) {
            Usuario admin = new Usuario();
            admin.setNombre("Administrador Brixo");
            admin.setEmail("admin@brixo.com");
            admin.setPassword(passwordEncoder.encode("Admin123*"));
            admin.setRol(rolAdmin);
            usuarioRepository.save(admin);
        }
    }
}
