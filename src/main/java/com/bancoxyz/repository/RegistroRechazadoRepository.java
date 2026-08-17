package com.bancoxyz.repository;

import com.bancoxyz.entity.RegistroRechazado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Acceso a la bitacora de registros omitidos o filtrados durante la migracion. */
@Repository
public interface RegistroRechazadoRepository extends JpaRepository<RegistroRechazado, Long> {

    List<RegistroRechazado> findByJobExecutionIdOrderByIdAsc(Long jobExecutionId);

    long countByJobExecutionId(Long jobExecutionId);

    long countByJobExecutionIdAndClasificacion(Long jobExecutionId, String clasificacion);
}
