package com.BRIXO.repository;

import com.BRIXO.model.SolicitudContratista;
import com.BRIXO.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SolicitudContratistaRepository extends JpaRepository<SolicitudContratista, Long> {
    List<SolicitudContratista> findByEstado(SolicitudContratista.Estado estado);
    boolean existsByUsuarioAndEstado(Usuario usuario, SolicitudContratista.Estado estado);
    long countByEstado(SolicitudContratista.Estado estado);
}
