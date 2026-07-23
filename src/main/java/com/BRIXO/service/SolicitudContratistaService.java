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
    private final NotificacionService notificacionService;

    public SolicitudContratistaService(SolicitudContratistaRepository solicitudRepository,
                                        UsuarioRepository usuarioRepository,
                                        RolRepository rolRepository,
                                        NotificacionService notificacionService) {
        this.solicitudRepository = solicitudRepository;
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.notificacionService = notificacionService;
    }

    /** Solo verifica que el usuario pueda aplicar — NO guarda nada en BD. */
    public void validarElegibilidad(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (usuario.isContratistaAprobado() ||
                (usuario.getRol() != null && "CONTRATISTA".equalsIgnoreCase(usuario.getRol().getNombre()))) {
            throw new IllegalArgumentException("Ya eres contratista");
        }
        if (solicitudRepository.existsByUsuarioAndEstado(usuario, SolicitudContratista.Estado.PENDIENTE)) {
            throw new IllegalArgumentException("Ya tienes una solicitud pendiente");
        }
    }

    /** Crea la solicitud completa con todos los documentos y notifica al admin. */
    @Transactional
    public void crearYGuardar(String email, String numeroCedula,
                               boolean verificado, Double score, String error,
                               String documentoPath, String selfiePath,
                               String areaEspecialidad,
                               String certificadoPath, String reciboServiciosPath,
                               String antecedentesPath) {

        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        // Re-validar al momento de guardar (doble verificación)
        if (usuario.isContratistaAprobado() ||
                (usuario.getRol() != null && "CONTRATISTA".equalsIgnoreCase(usuario.getRol().getNombre()))) {
            throw new IllegalArgumentException("Ya eres contratista");
        }
        if (solicitudRepository.existsByUsuarioAndEstado(usuario, SolicitudContratista.Estado.PENDIENTE)) {
            throw new IllegalArgumentException("Ya tienes una solicitud pendiente");
        }

        SolicitudContratista solicitud = new SolicitudContratista();
        solicitud.setUsuario(usuario);
        solicitud.setEstado(SolicitudContratista.Estado.PENDIENTE);
        solicitud.setFechaSolicitud(LocalDateTime.now());
        solicitud.setNumeroCedula(numeroCedula);
        solicitud.setIdentidadVerificada(verificado);
        solicitud.setVerificacionScore(score);
        solicitud.setVerificacionError(error);
        solicitud.setDocumentoPath(documentoPath);
        solicitud.setSelfiePath(selfiePath);
        if (areaEspecialidad != null && !areaEspecialidad.isBlank())
            solicitud.setAreaEspecialidad(areaEspecialidad.trim());
        solicitud.setCertificadoPath(certificadoPath);
        solicitud.setReciboServiciosPath(reciboServiciosPath);
        solicitud.setAntecedentesPath(antecedentesPath);
        solicitudRepository.save(solicitud);

        // Notificar al admin solo cuando la solicitud esté completa
        usuarioRepository.findByRolNombre("ADMIN").forEach(admin ->
                notificacionService.crear(
                        admin.getEmail(),
                        "Nueva solicitud de contratista de " + usuario.getNombre() + " — revisa la identidad y aprueba o rechaza.",
                        "/admin/solicitudes"
                )
        );
    }

    @Transactional
    public void guardarVerificacion(Long solicitudId, String numeroCedula,
                                    boolean verificado, Double score, String error,
                                    String documentoPath, String selfiePath,
                                    String areaEspecialidad,
                                    String certificadoPath,
                                    String reciboServiciosPath,
                                    String antecedentesPath) {
        SolicitudContratista solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new IllegalArgumentException("Solicitud no encontrada"));
        solicitud.setNumeroCedula(numeroCedula);
        solicitud.setIdentidadVerificada(verificado);
        solicitud.setVerificacionScore(score);
        solicitud.setVerificacionError(error);
        solicitud.setDocumentoPath(documentoPath);
        solicitud.setSelfiePath(selfiePath);
        if (areaEspecialidad != null && !areaEspecialidad.isBlank())
            solicitud.setAreaEspecialidad(areaEspecialidad.trim());
        solicitud.setCertificadoPath(certificadoPath);
        solicitud.setReciboServiciosPath(reciboServiciosPath);
        solicitud.setAntecedentesPath(antecedentesPath);
        solicitudRepository.save(solicitud);
    }

    public SolicitudContratista obtenerUltimaSolicitud(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email).orElse(null);
        if (usuario == null) return null;
        return solicitudRepository.findFirstByUsuarioOrderByFechaSolicitudDesc(usuario).orElse(null);
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
        usuario.setContratistaAprobado(true);
        usuarioRepository.save(usuario);

        notificacionService.crear(
                usuario.getEmail(),
                "¡Felicitaciones! Tu solicitud para ser contratista fue aprobada. Ya puedes ofrecer servicios en Brixo.",
                "/dashboard"
        );
    }

    @Transactional
    public void rechazar(Long solicitudId, String motivoRechazo) {
        SolicitudContratista solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new IllegalArgumentException("Solicitud no encontrada"));

        solicitud.setEstado(SolicitudContratista.Estado.RECHAZADA);
        solicitud.setFechaRespuesta(LocalDateTime.now());
        solicitud.setMotivoRechazo(motivoRechazo != null ? motivoRechazo.trim() : null);
        solicitudRepository.save(solicitud);

        String msgMotivo = (motivoRechazo != null && !motivoRechazo.isBlank())
                ? " Motivo: " + motivoRechazo.trim()
                : "";
        notificacionService.crear(
                solicitud.getUsuario().getEmail(),
                "Tu solicitud para ser contratista fue rechazada." + msgMotivo + " Puedes volver a intentarlo o contactar al soporte.",
                "/dashboard"
        );
    }

    public boolean tieneSolicitudPendiente(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email).orElse(null);
        if (usuario == null) return false;
        return solicitudRepository.existsByUsuarioAndEstado(usuario, SolicitudContratista.Estado.PENDIENTE);
    }
}
