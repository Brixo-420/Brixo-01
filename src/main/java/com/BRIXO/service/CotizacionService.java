package com.BRIXO.service;

import com.BRIXO.model.Cotizacion;
import com.BRIXO.model.EstadoCotizacion;
import com.BRIXO.model.EstadoServicio;
import com.BRIXO.model.Servicio;
import com.BRIXO.model.Usuario;
import com.BRIXO.repository.CotizacionRepository;
import com.BRIXO.repository.ServicioRepository;
import com.BRIXO.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CotizacionService {

    private final CotizacionRepository cotizacionRepository;
    private final ServicioRepository servicioRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificacionService notificacionService;

    public CotizacionService(CotizacionRepository cotizacionRepository,
                             ServicioRepository servicioRepository,
                             UsuarioRepository usuarioRepository,
                             NotificacionService notificacionService) {
        this.cotizacionRepository = cotizacionRepository;
        this.servicioRepository = servicioRepository;
        this.usuarioRepository = usuarioRepository;
        this.notificacionService = notificacionService;
    }

    public List<Cotizacion> listarPorServicio(Long servicioId) {
        return cotizacionRepository.findByServicioIdOrderByFechaCreacionDesc(servicioId);
    }

    @Transactional
    public Cotizacion crearCotizacion(Long servicioId, String emailContratista, BigDecimal monto, String mensaje) {
        Servicio servicio = servicioRepository.findById(servicioId)
                .orElseThrow(() -> new IllegalArgumentException("Servicio no encontrado"));

        if (servicio.getEstado() != EstadoServicio.ABIERTO) {
            throw new IllegalArgumentException("Solo se puede cotizar servicios en estado ABIERTO");
        }

        Usuario contratista = usuarioRepository.findByEmail(emailContratista)
                .orElseThrow(() -> new IllegalArgumentException("Contratista no encontrado"));

        if (contratista.getRol() == null || !"CONTRATISTA".equals(contratista.getRol().getNombre())) {
            throw new IllegalArgumentException("Solo un contratista puede enviar cotizaciones");
        }

        if (cotizacionRepository.existsByServicioIdAndContratistaEmailAndEstado(servicioId, emailContratista, EstadoCotizacion.PENDIENTE)) {
            throw new IllegalArgumentException("Ya tienes una cotización pendiente para este servicio");
        }

        Cotizacion cotizacion = new Cotizacion();
        cotizacion.setServicio(servicio);
        cotizacion.setContratista(contratista);
        cotizacion.setMonto(monto);
        cotizacion.setMensaje(mensaje);
        cotizacionRepository.save(cotizacion);

        // Notificar al cliente
        String clienteEmail = servicio.getCliente().getEmail();
        notificacionService.crear(clienteEmail,
                contratista.getNombre() + " envió una cotización de $" + monto + " para \"" + servicio.getTitulo() + "\"",
                "/servicios/" + servicioId + "/detalle");

        return cotizacion;
    }

    @Transactional
    public void aceptarCotizacion(Long cotizacionId, String emailCliente) {
        Cotizacion cotizacion = cotizacionRepository.findById(cotizacionId)
                .orElseThrow(() -> new IllegalArgumentException("Cotización no encontrada"));

        Servicio servicio = cotizacion.getServicio();

        // Validar que el cliente sea el dueño del servicio
        if (!servicio.getCliente().getEmail().equals(emailCliente)) {
            throw new IllegalArgumentException("Solo el dueño del servicio puede aceptar cotizaciones");
        }

        if (cotizacion.getEstado() != EstadoCotizacion.PENDIENTE) {
            throw new IllegalArgumentException("Esta cotización ya fue procesada");
        }

        // Aceptar esta cotización
        cotizacion.setEstado(EstadoCotizacion.ACEPTADA);
        cotizacion.setFechaRespuesta(LocalDateTime.now());
        cotizacionRepository.save(cotizacion);

        // Rechazar las demás cotizaciones pendientes de este servicio
        List<Cotizacion> otras = cotizacionRepository.findByServicioIdOrderByFechaCreacionDesc(servicio.getId());
        for (Cotizacion otra : otras) {
            if (!otra.getId().equals(cotizacionId) && otra.getEstado() == EstadoCotizacion.PENDIENTE) {
                otra.setEstado(EstadoCotizacion.RECHAZADA);
                otra.setFechaRespuesta(LocalDateTime.now());
                cotizacionRepository.save(otra);

                // Notificar al contratista rechazado
                notificacionService.crear(otra.getContratista().getEmail(),
                        "Tu cotización para \"" + servicio.getTitulo() + "\" fue rechazada (se aceptó otra oferta)",
                        "/servicios/" + servicio.getId() + "/detalle");
            }
        }

        // Asignar contratista al servicio y cambiar estado
        servicio.setContratistaAsignado(cotizacion.getContratista());
        servicio.setEstado(EstadoServicio.EN_COTIZACION);
        servicioRepository.save(servicio);

        // Notificar al contratista aceptado
        notificacionService.crear(cotizacion.getContratista().getEmail(),
                "¡Tu cotización de $" + cotizacion.getMonto() + " fue aceptada para \"" + servicio.getTitulo() + "\"! Ya puedes iniciar el trabajo.",
                "/servicios/" + servicio.getId() + "/detalle");
    }

    @Transactional
    public void rechazarCotizacion(Long cotizacionId, String emailCliente) {
        Cotizacion cotizacion = cotizacionRepository.findById(cotizacionId)
                .orElseThrow(() -> new IllegalArgumentException("Cotización no encontrada"));

        Servicio servicio = cotizacion.getServicio();

        if (!servicio.getCliente().getEmail().equals(emailCliente)) {
            throw new IllegalArgumentException("Solo el dueño del servicio puede rechazar cotizaciones");
        }

        if (cotizacion.getEstado() != EstadoCotizacion.PENDIENTE) {
            throw new IllegalArgumentException("Esta cotización ya fue procesada");
        }

        cotizacion.setEstado(EstadoCotizacion.RECHAZADA);
        cotizacion.setFechaRespuesta(LocalDateTime.now());
        cotizacionRepository.save(cotizacion);

        // Notificar al contratista
        notificacionService.crear(cotizacion.getContratista().getEmail(),
                "Tu cotización para \"" + servicio.getTitulo() + "\" fue rechazada",
                "/servicios/" + servicio.getId() + "/detalle");
    }
}
