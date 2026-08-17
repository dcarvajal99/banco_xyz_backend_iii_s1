package com.bancoxyz.repository;

import com.bancoxyz.entity.EstadoCuentaAnual;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Acceso a los estados de cuenta anuales compilados para auditoria. */
@Repository
public interface EstadoCuentaAnualRepository extends JpaRepository<EstadoCuentaAnual, Long> {

    List<EstadoCuentaAnual> findByJobExecutionIdOrderByCuentaIdAscAnioAsc(Long jobExecutionId);
}
