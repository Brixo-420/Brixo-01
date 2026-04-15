package com.BRIXO.service;

import com.BRIXO.model.EstadoServicio;
import com.BRIXO.model.Servicio;
import com.BRIXO.model.Usuario;
import com.BRIXO.repository.ServicioRepository;
import com.BRIXO.repository.UsuarioRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ServicioService {

    private final ServicioRepository servicioRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificacionService notificacionService;

    public ServicioService(ServicioRepository servicioRepository, UsuarioRepository usuarioRepository, NotificacionService notificacionService) {
        this.servicioRepository = servicioRepository;
        this.usuarioRepository = usuarioRepository;
        this.notificacionService = notificacionService;
    }

    public List<Servicio> listarFiltrados(String titulo, String estado, String emailCliente, String usuarioActualEmail, boolean esAdmin, boolean esCliente, boolean esContratista) {
        Specification<Servicio> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // LEFT JOINs para evitar excluir servicios con cliente/contratista null
            Join<Object, Object> clienteJoin = root.join("cliente", JoinType.LEFT);
            Join<Object, Object> contratistaJoin = root.join("contratistaAsignado", JoinType.LEFT);

            // Búsqueda por título, nombre de cliente o nombre de contratista
            if (titulo != null && !titulo.isBlank()) {
                String pattern = "%" + titulo.toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("titulo")), pattern),
                    cb.like(cb.lower(clienteJoin.get("nombre")), pattern),
                    cb.like(cb.lower(contratistaJoin.get("nombre")), pattern)
                ));
            }

            if (estado != null && !estado.isBlank()) {
                predicates.add(cb.equal(root.get("estado"), EstadoServicio.valueOf(estado)));
            }

            if (emailCliente != null && !emailCliente.isBlank() && esAdmin) {
                predicates.add(cb.like(cb.lower(clienteJoin.get("email")), "%" + emailCliente.toLowerCase() + "%"));
            }

            // CLIENTE: solo sus propios servicios
            if (esCliente && !esAdmin) {
                predicates.add(cb.equal(clienteJoin.get("email"), usuarioActualEmail));
            }

            // CONTRATISTA: servicios abiertos/en_cotizacion + asignados a él (cualquier estado)
            if (esContratista && !esAdmin) {
                Predicate abiertos = root.get("estado").in(EstadoServicio.ABIERTO, EstadoServicio.EN_COTIZACION);
                Predicate asignados = cb.equal(contratistaJoin.get("email"), usuarioActualEmail);
                predicates.add(cb.or(abiertos, asignados));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return servicioRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "fechaCreacion"));
    }

    public Servicio buscarPorId(Long id) {
        return servicioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Servicio no encontrado"));
    }

    @Transactional
    public Servicio crear(Servicio servicio, String emailUsuarioActual) {
        Usuario cliente = usuarioRepository.findByEmail(emailUsuarioActual)
                .orElseThrow(() -> new IllegalArgumentException("Usuario autenticado no encontrado"));

        servicio.setId(null);
        servicio.setCliente(cliente);
        return servicioRepository.save(servicio);
    }

    @Transactional
    public Servicio actualizar(Long id, Servicio cambios, String emailUsuarioActual, boolean esAdmin) {
        Servicio actual = buscarPorId(id);
        validarPermisoPropietario(actual, emailUsuarioActual, esAdmin);
        validarEdicionClienteAntesDeInicio(actual, esAdmin);

        actual.setTitulo(cambios.getTitulo());
        actual.setDescripcion(cambios.getDescripcion());
        actual.setUbicacion(cambios.getUbicacion());
        actual.setPresupuesto(cambios.getPresupuesto());
        
        // Solo ADMIN puede cambiar el estado del servicio
        // Si es ADMIN y envía un estado válido, actualizar; si no es ADMIN, ignorar cambios de estado
        if (esAdmin && cambios.getEstado() != null) {
            actual.setEstado(cambios.getEstado());
        }

        return servicioRepository.save(actual);
    }

    @Transactional
    public void eliminar(Long id, String emailUsuarioActual, boolean esAdmin) {
        Servicio actual = buscarPorId(id);
        validarPermisoPropietario(actual, emailUsuarioActual, esAdmin);
        validarEdicionClienteAntesDeInicio(actual, esAdmin);
        servicioRepository.delete(actual);
    }

    @Transactional
    public void asignarContratista(Long id, String emailContratista, boolean esAdmin) {
        Servicio actual = buscarPorId(id);

        if (actual.getEstado() != EstadoServicio.ABIERTO && actual.getEstado() != EstadoServicio.EN_COTIZACION) {
            throw new IllegalArgumentException("Solo se pueden asignar contratistas a servicios ABIERTO o EN_COTIZACION");
        }

        Usuario contratista = usuarioRepository.findByEmail(emailContratista)
                .orElseThrow(() -> new IllegalArgumentException("Contratista no encontrado"));

        if (contratista.getRol() == null || !"CONTRATISTA".equals(contratista.getRol().getNombre())) {
            throw new IllegalArgumentException("El usuario autenticado no tiene rol CONTRATISTA");
        }

        if (!esAdmin && actual.getContratistaAsignado() != null
                && !emailContratista.equals(actual.getContratistaAsignado().getEmail())) {
            throw new IllegalArgumentException("Este servicio ya esta asignado a otro contratista");
        }

        actual.setContratistaAsignado(contratista);
        servicioRepository.save(actual);
    }

    @Transactional
    public void iniciarServicio(Long id, String emailUsuarioActual, boolean esAdmin) {
        Servicio actual = buscarPorId(id);
        if (actual.getEstado() != EstadoServicio.ABIERTO && actual.getEstado() != EstadoServicio.EN_COTIZACION) {
            throw new IllegalArgumentException("Solo se pueden iniciar servicios en estado ABIERTO o EN_COTIZACION");
        }

        validarContratistaAsignado(actual, emailUsuarioActual, esAdmin);

        actual.setEstado(EstadoServicio.EN_PROCESO);
        servicioRepository.save(actual);

        // Notificar al cliente que el contratista inició el trabajo
        if (actual.getCliente() != null && actual.getContratistaAsignado() != null) {
            notificacionService.crear(actual.getCliente().getEmail(),
                    actual.getContratistaAsignado().getNombre() + " ha iniciado el trabajo en \"" + actual.getTitulo() + "\"",
                    "/servicios/" + actual.getId() + "/detalle");
        }
    }

    @Transactional
    public void finalizarServicio(Long id, String emailUsuarioActual, boolean esAdmin) {
        Servicio actual = buscarPorId(id);
        if (actual.getEstado() != EstadoServicio.EN_PROCESO) {
            throw new IllegalArgumentException("Solo se pueden finalizar servicios en estado EN_PROCESO");
        }

        validarContratistaAsignado(actual, emailUsuarioActual, esAdmin);

        actual.setEstado(EstadoServicio.CERRADO);
        servicioRepository.save(actual);
    }

    private void validarPermisoPropietario(Servicio servicio, String emailUsuarioActual, boolean esAdmin) {
        if (esAdmin) {
            return;
        }

        String propietario = servicio.getCliente() != null ? servicio.getCliente().getEmail() : null;
        if (propietario == null || !propietario.equals(emailUsuarioActual)) {
            throw new IllegalArgumentException("No tienes permisos para modificar este servicio");
        }
    }

    private void validarEdicionClienteAntesDeInicio(Servicio servicio, boolean esAdmin) {
        if (esAdmin) {
            return;
        }

        if (servicio.getEstado() != EstadoServicio.ABIERTO) {
            throw new IllegalArgumentException("No puedes editar o eliminar el servicio despues de iniciado");
        }
    }

    private void validarContratistaAsignado(Servicio servicio, String emailUsuarioActual, boolean esAdmin) {
        if (servicio.getContratistaAsignado() == null) {
            throw new IllegalArgumentException("Primero debe asignarse un contratista al servicio");
        }

        if (esAdmin) {
            return;
        }

        String asignado = servicio.getContratistaAsignado().getEmail();
        if (!asignado.equals(emailUsuarioActual)) {
            throw new IllegalArgumentException("Solo el contratista asignado puede ejecutar esta accion");
        }
    }
}
