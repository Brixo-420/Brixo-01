package com.BRIXO.repository;

import com.BRIXO.model.Cotizacion;
import com.BRIXO.model.EstadoCotizacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CotizacionRepository extends JpaRepository<Cotizacion, Long> {

    List<Cotizacion> findByServicioIdOrderByFechaCreacionDesc(Long servicioId);

    boolean existsByServicioIdAndContratistaEmailAndEstado(Long servicioId, String email, EstadoCotizacion estado);

    boolean existsByServicioIdAndContratistaEmail(Long servicioId, String email);

    long countByEstado(EstadoCotizacion estado);
}
