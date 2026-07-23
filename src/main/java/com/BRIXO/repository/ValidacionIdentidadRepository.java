package com.BRIXO.repository;

import com.BRIXO.model.ValidacionIdentidad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ValidacionIdentidadRepository extends JpaRepository<ValidacionIdentidad, Long> {
    Optional<ValidacionIdentidad> findByUsuarioId(Long usuarioId);
}
