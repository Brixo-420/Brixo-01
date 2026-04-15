package com.BRIXO.service;

import com.BRIXO.model.Rol;
import com.BRIXO.model.SolicitudContratista;
import com.BRIXO.model.Usuario;
import com.BRIXO.repository.RolRepository;
import com.BRIXO.repository.SolicitudContratistaRepository;
import com.BRIXO.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SolicitudContratistaService {

    private final SolicitudContratistaRepository solicitudRepository;
    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;

    public SolicitudContratistaService(SolicitudContratistaRepository solicitudRepository,
                                        UsuarioRepository usuarioRepository,
                                        RolRepository rolRepository) {
        this.solicitudRepository = solicitudRepository;
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
    }

    @Transactional
    public SolicitudContratista crearSolicitud(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (usuario.getRol() != null && "CONTRATISTA".equalsIgnoreCase(usuario.getRol().getNombre())) {
            throw new IllegalArgumentException("Ya eres contratista");
        }

        if (solicitudRepository.existsByUsuarioAndEstado(usuario, SolicitudContratista.Estado.PENDIENTE)) {
            throw new IllegalArgumentException("Ya tienes una solicitud pendiente");
        }

        SolicitudContratista solicitud = new SolicitudContratista();
        solicitud.setUsuario(usuario);
        solicitud.setEstado(SolicitudContratista.Estado.PENDIENTE);
        solicitud.setFechaSolicitud(LocalDateTime.now());
        return solicitudRepository.save(solicitud);
    }

    public List<SolicitudContratista> listarPendientes() {
        return solicitudRepository.findByEstado(SolicitudContratista.Estado.PENDIENTE);
    }

    public List<SolicitudContratista> listarTodas() {
        return solicitudRepository.findAll();
    }

    public long contarPendientes() {
        return solicitudRepository.countByEstado(SolicitudContratista.Estado.PENDIENTE);
    }

    @Transactional
    public void aprobar(Long solicitudId) {
        SolicitudContratista solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new IllegalArgumentException("Solicitud no encontrada"));

        Rol rolContratista = rolRepository.findByNombre("CONTRATISTA")
                .orElseThrow(() -> new IllegalArgumentException("Rol CONTRATISTA no encontrado"));

        solicitud.setEstado(SolicitudContratista.Estado.APROBADA);
        solicitud.setFechaRespuesta(LocalDateTime.now());
        solicitudRepository.save(solicitud);

        Usuario usuario = solicitud.getUsuario();
        usuario.setRol(rolContratista);
        usuarioRepository.save(usuario);
    }

    @Transactional
    public void rechazar(Long solicitudId) {
        SolicitudContratista solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new IllegalArgumentException("Solicitud no encontrada"));

        solicitud.setEstado(SolicitudContratista.Estado.RECHAZADA);
        solicitud.setFechaRespuesta(LocalDateTime.now());
        solicitudRepository.save(solicitud);
    }

    public boolean tieneSolicitudPendiente(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email).orElse(null);
        if (usuario == null) return false;
        return solicitudRepository.existsByUsuarioAndEstado(usuario, SolicitudContratista.Estado.PENDIENTE);
    }
}
