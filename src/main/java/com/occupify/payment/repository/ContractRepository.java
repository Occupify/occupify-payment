package com.occupify.payment.repository;

import com.occupify.payment.entity.Contract;
import com.occupify.payment.enums.ContractStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ContractRepository extends JpaRepository<Contract, UUID> {

    List<Contract> findByJobId(UUID jobId);

    @Query("SELECT c FROM Contract c WHERE c.employer.id = :employerId")
    Page<Contract> findByEmployerId(@Param("employerId") UUID employerId, Pageable pageable);

    @Query("SELECT c FROM Contract c WHERE c.employee.id = :employeeId")
    Page<Contract> findByEmployeeId(@Param("employeeId") UUID employeeId, Pageable pageable);

    Page<Contract> findByEmployer_Id(UUID employerId, Pageable pageable);

    Page<Contract> findByEmployee_Id(UUID employeeId, Pageable pageable);

    List<Contract> findByStatus(ContractStatus status);
}
