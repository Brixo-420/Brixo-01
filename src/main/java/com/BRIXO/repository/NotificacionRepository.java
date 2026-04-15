package com.BRIXO.repository;

import com.BRIXO.model.Notificacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    List<Notificacion> findByUsuarioEmailOrderByFechaCreacionDesc(String email);

    List<Notificacion> findTop20ByUsuarioEmailOrderByFechaCreacionDesc(String email);

    long countByUsuarioEmailAndLeidaFalse(String email);
}
