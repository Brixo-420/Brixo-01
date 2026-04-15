package com.BRIXO.service;

import com.BRIXO.model.Notificacion;
import com.BRIXO.model.Usuario;
import com.BRIXO.repository.NotificacionRepository;
import com.BRIXO.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificacionService {

    private final NotificacionRepository notificacionRepository;
    private final UsuarioRepository usuarioRepository;

    public NotificacionService(NotificacionRepository notificacionRepository, UsuarioRepository usuarioRepository) {
        this.notificacionRepository = notificacionRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public void crear(String emailDestinatario, String mensaje, String url) {
        Usuario usuario = usuarioRepository.findByEmail(emailDestinatario).orElse(null);
        if (usuario == null) return;

        Notificacion n = new Notificacion();
        n.setUsuario(usuario);
        n.setMensaje(mensaje);
        n.setUrl(url);
        notificacionRepository.save(n);
    }

    public List<Notificacion> listarPorUsuario(String email) {
        return notificacionRepository.findTop20ByUsuarioEmailOrderByFechaCreacionDesc(email);
    }

    public long contarNoLeidas(String email) {
        return notificacionRepository.countByUsuarioEmailAndLeidaFalse(email);
    }

    @Transactional
    public void marcarLeida(Long id, String email) {
        Notificacion n = notificacionRepository.findById(id).orElse(null);
        if (n != null && n.getUsuario().getEmail().equals(email)) {
            n.setLeida(true);
            notificacionRepository.save(n);
        }
    }

    @Transactional
    public void marcarTodasLeidas(String email) {
        List<Notificacion> noLeidas = notificacionRepository.findByUsuarioEmailOrderByFechaCreacionDesc(email);
        for (Notificacion n : noLeidas) {
            if (!n.isLeida()) {
                n.setLeida(true);
            }
        }
        notificacionRepository.saveAll(noLeidas);
    }
}
