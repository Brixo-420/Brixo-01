package com.BRIXO.repository;

import com.BRIXO.model.EstadoServicio;
import com.BRIXO.model.Servicio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ServicioRepository extends JpaRepository<Servicio, Long>, JpaSpecificationExecutor<Servicio> {

    List<Servicio> findByEstadoAndClienteEmailOrderByFechaCreacionDesc(EstadoServicio estado, String email);

    List<Servicio> findByEstadoAndContratistaAsignadoEmailOrderByFechaCreacionDesc(EstadoServicio estado, String email);
}
