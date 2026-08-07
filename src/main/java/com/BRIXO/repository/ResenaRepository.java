package com.BRIXO.repository;

import com.BRIXO.model.Resena;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ResenaRepository extends JpaRepository<Resena, Long> {

    List<Resena> findByServicioIdOrderByFechaCreacionDesc(Long servicioId);

    List<Resena> findByDestinatarioIdOrderByFechaCreacionDesc(Long destinatarioId);

    List<Resena> findByDestinatarioEmailOrderByFechaCreacionDesc(String email);

    List<Resena> findByAutorEmailOrderByFechaCreacionDesc(String email);

    Optional<Resena> findByServicioIdAndAutorEmail(Long servicioId, String email);

    boolean existsByServicioIdAndAutorEmail(Long servicioId, String email);

    long countByDestinatarioId(Long destinatarioId);

    /** Promedio de estrellas recibidas; 0 si el usuario aun no tiene resenas. */
    @Query("select coalesce(avg(r.calificacion), 0) from Resena r where r.destinatario.id = :usuarioId")
    double promedioPorDestinatario(@Param("usuarioId") Long usuarioId);

    /** Pares [calificacion, cantidad] para pintar la distribucion de 1 a 5 estrellas. */
    @Query("select r.calificacion, count(r) from Resena r where r.destinatario.id = :usuarioId group by r.calificacion")
    List<Object[]> distribucionPorDestinatario(@Param("usuarioId") Long usuarioId);
}
