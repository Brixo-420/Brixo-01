package com.BRIXO.service;

import com.BRIXO.dto.ResumenResenas;
import com.BRIXO.model.EstadoServicio;
import com.BRIXO.model.Resena;
import com.BRIXO.model.Servicio;
import com.BRIXO.model.TipoResena;
import com.BRIXO.model.Usuario;
import com.BRIXO.repository.ResenaRepository;
import com.BRIXO.repository.ServicioRepository;
import com.BRIXO.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ResenaService {

    private final ResenaRepository resenaRepository;
    private final ServicioRepository servicioRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificacionService notificacionService;

    public ResenaService(ResenaRepository resenaRepository,
                         ServicioRepository servicioRepository,
                         UsuarioRepository usuarioRepository,
                         NotificacionService notificacionService) {
        this.resenaRepository = resenaRepository;
        this.servicioRepository = servicioRepository;
        this.usuarioRepository = usuarioRepository;
        this.notificacionService = notificacionService;
    }

    // ── Consultas ────────────────────────────────────────────────────────────

    public List<Resena> listarPorServicio(Long servicioId) {
        return resenaRepository.findByServicioIdOrderByFechaCreacionDesc(servicioId);
    }

    public List<Resena> listarRecibidas(String email) {
        return resenaRepository.findByDestinatarioEmailOrderByFechaCreacionDesc(email);
    }

    public List<Resena> listarEscritas(String email) {
        return resenaRepository.findByAutorEmailOrderByFechaCreacionDesc(email);
    }

    public List<Resena> listarRecibidasPorUsuario(Long usuarioId) {
        return resenaRepository.findByDestinatarioIdOrderByFechaCreacionDesc(usuarioId);
    }

    public Resena buscarMiResena(Long servicioId, String email) {
        return resenaRepository.findByServicioIdAndAutorEmail(servicioId, email).orElse(null);
    }

    public ResumenResenas resumen(Long usuarioId) {
        long total = resenaRepository.countByDestinatarioId(usuarioId);
        if (total == 0) {
            return ResumenResenas.vacio();
        }

        Map<Integer, Long> conteos = new HashMap<>();
        for (Object[] fila : resenaRepository.distribucionPorDestinatario(usuarioId)) {
            conteos.put(((Number) fila[0]).intValue(), ((Number) fila[1]).longValue());
        }

        return new ResumenResenas(resenaRepository.promedioPorDestinatario(usuarioId), total, conteos);
    }

    public ResumenResenas resumenPorEmail(String email) {
        return usuarioRepository.findByEmail(email)
                .map(u -> resumen(u.getId()))
                .orElseGet(ResumenResenas::vacio);
    }

    /**
     * Servicios cerrados en los que el usuario participo y todavia no califico
     * a la otra parte.
     */
    public List<Servicio> pendientesPorResenar(String email) {
        List<Servicio> candidatos = new ArrayList<>();
        candidatos.addAll(servicioRepository.findByEstadoAndClienteEmailOrderByFechaCreacionDesc(EstadoServicio.CERRADO, email));
        candidatos.addAll(servicioRepository.findByEstadoAndContratistaAsignadoEmailOrderByFechaCreacionDesc(EstadoServicio.CERRADO, email));

        List<Servicio> pendientes = new ArrayList<>();
        for (Servicio servicio : candidatos) {
            if (servicio.getCliente() != null && servicio.getContratistaAsignado() != null
                    && !resenaRepository.existsByServicioIdAndAutorEmail(servicio.getId(), email)) {
                pendientes.add(servicio);
            }
        }
        return pendientes;
    }

    /** true si el usuario puede dejar (todavia no dejo) una resena en el servicio. */
    public boolean puedeResenar(Servicio servicio, String email) {
        return esParticipante(servicio, email)
                && servicio.getEstado() == EstadoServicio.CERRADO
                && !resenaRepository.existsByServicioIdAndAutorEmail(servicio.getId(), email);
    }

    // ── Comandos ─────────────────────────────────────────────────────────────

    @Transactional
    public Resena crear(Long servicioId, String emailAutor, Integer calificacion, String comentario) {
        Servicio servicio = servicioRepository.findById(servicioId)
                .orElseThrow(() -> new IllegalArgumentException("Servicio no encontrado"));

        if (servicio.getEstado() != EstadoServicio.CERRADO) {
            throw new IllegalArgumentException("Solo puedes dejar una resena cuando el servicio esta finalizado");
        }

        Usuario cliente = servicio.getCliente();
        Usuario contratista = servicio.getContratistaAsignado();
        if (cliente == null || contratista == null) {
            throw new IllegalArgumentException("El servicio no tiene cliente y contratista para calificar");
        }

        Usuario autor = usuarioRepository.findByEmail(emailAutor)
                .orElseThrow(() -> new IllegalArgumentException("Usuario autenticado no encontrado"));

        Usuario destinatario;
        TipoResena tipo;
        if (emailAutor.equals(cliente.getEmail())) {
            destinatario = contratista;
            tipo = TipoResena.CLIENTE_A_CONTRATISTA;
        } else if (emailAutor.equals(contratista.getEmail())) {
            destinatario = cliente;
            tipo = TipoResena.CONTRATISTA_A_CLIENTE;
        } else {
            throw new IllegalArgumentException("Solo el cliente y el contratista del servicio pueden dejar una resena");
        }

        if (resenaRepository.existsByServicioIdAndAutorEmail(servicioId, emailAutor)) {
            throw new IllegalArgumentException("Ya dejaste una resena para este servicio");
        }

        Resena resena = new Resena();
        resena.setServicio(servicio);
        resena.setAutor(autor);
        resena.setDestinatario(destinatario);
        resena.setCalificacion(validarCalificacion(calificacion));
        resena.setComentario(limpiarComentario(comentario));
        resena.setTipo(tipo);
        resenaRepository.save(resena);

        notificacionService.crear(destinatario.getEmail(),
                autor.getNombre() + " te califico con " + resena.getCalificacion()
                        + " estrella(s) por \"" + servicio.getTitulo() + "\"",
                "/servicios/" + servicio.getId() + "/detalle");

        return resena;
    }

    @Transactional
    public Resena actualizar(Long resenaId, String emailAutor, Integer calificacion, String comentario) {
        Resena resena = resenaRepository.findById(resenaId)
                .orElseThrow(() -> new IllegalArgumentException("Resena no encontrada"));

        if (!resena.getAutor().getEmail().equals(emailAutor)) {
            throw new IllegalArgumentException("Solo puedes editar tus propias resenas");
        }

        resena.setCalificacion(validarCalificacion(calificacion));
        resena.setComentario(limpiarComentario(comentario));
        resena.setFechaEdicion(LocalDateTime.now());
        return resenaRepository.save(resena);
    }

    /** Moderacion: el administrador puede retirar una resena inapropiada. */
    @Transactional
    public void eliminar(Long resenaId, boolean esAdmin) {
        if (!esAdmin) {
            throw new IllegalArgumentException("Solo un administrador puede eliminar resenas");
        }

        Resena resena = resenaRepository.findById(resenaId)
                .orElseThrow(() -> new IllegalArgumentException("Resena no encontrada"));
        resenaRepository.delete(resena);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    public boolean esParticipante(Servicio servicio, String email) {
        boolean esCliente = servicio.getCliente() != null && servicio.getCliente().getEmail().equals(email);
        boolean esContratista = servicio.getContratistaAsignado() != null
                && servicio.getContratistaAsignado().getEmail().equals(email);
        return esCliente || esContratista;
    }

    private Integer validarCalificacion(Integer calificacion) {
        if (calificacion == null || calificacion < 1 || calificacion > 5) {
            throw new IllegalArgumentException("La calificacion debe estar entre 1 y 5 estrellas");
        }
        return calificacion;
    }

    private String limpiarComentario(String comentario) {
        if (comentario == null || comentario.isBlank()) {
            return null;
        }
        String limpio = comentario.trim();
        if (limpio.length() > 500) {
            throw new IllegalArgumentException("El comentario no puede superar 500 caracteres");
        }
        return limpio;
    }
}
