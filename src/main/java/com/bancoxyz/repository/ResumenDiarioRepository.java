package com.bancoxyz.repository;

import com.bancoxyz.entity.ResumenDiario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Acceso al reporte diario de transacciones generado por el Job 1. */
@Repository
public interface ResumenDiarioRepository extends JpaRepository<ResumenDiario, Long> {

    List<ResumenDiario> findByJobExecutionIdOrderByFechaAsc(Long jobExecutionId);
}
