package com.occupify.payment.repository;

import com.occupify.payment.entity.Contract;
import com.occupify.payment.enums.ContractStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ContractRepository extends JpaRepository<Contract, UUID> {
    List<Contract> findByJobId(UUID jobId);
    Page<Contract> findByFreelancerId(UUID freelancerId, Pageable pageable);
    List<Contract> findByStatus(ContractStatus status);
}
