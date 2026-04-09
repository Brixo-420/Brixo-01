package com.BRIXO.service;

import com.BRIXO.model.Rol;
import com.BRIXO.model.Usuario;
import com.BRIXO.repository.RolRepository;
import com.BRIXO.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, RolRepository rolRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    public List<Rol> listarRoles() {
        return rolRepository.findAll();
    }

    public Usuario buscarPorId(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
    }

    @Transactional
    public Usuario crear(Usuario usuario, Long rolId) {
        if (usuarioRepository.existsByEmail(usuario.getEmail())) {
            throw new IllegalArgumentException("El correo ya esta registrado");
        }

        if (usuario.getPassword() == null || usuario.getPassword().isBlank() || usuario.getPassword().length() < 6) {
            throw new IllegalArgumentException("La contraseña debe tener minimo 6 caracteres");
        }

        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
        usuario.setRol(obtenerRol(rolId));
        return usuarioRepository.save(usuario);
    }

    @Transactional
    public Usuario actualizar(Long id, Usuario cambios, Long rolId) {
        Usuario actual = buscarPorId(id);

        if (!actual.getEmail().equals(cambios.getEmail()) && usuarioRepository.existsByEmail(cambios.getEmail())) {
            throw new IllegalArgumentException("El correo ya esta registrado");
        }

        actual.setNombre(cambios.getNombre());
        actual.setEmail(cambios.getEmail());
        actual.setRol(obtenerRol(rolId));

        if (cambios.getPassword() != null && !cambios.getPassword().isBlank()) {
            actual.setPassword(passwordEncoder.encode(cambios.getPassword()));
        }

        return usuarioRepository.save(actual);
    }

    public void eliminar(Long id) {
        usuarioRepository.deleteById(id);
    }

    private Rol obtenerRol(Long rolId) {
        if (rolId == null) {
            throw new IllegalArgumentException("Debe seleccionar un rol");
        }
        return rolRepository.findById(rolId)
                .orElseThrow(() -> new IllegalArgumentException("Rol no encontrado"));
    }
}
