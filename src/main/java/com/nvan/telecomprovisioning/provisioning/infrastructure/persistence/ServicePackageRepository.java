package com.nvan.telecomprovisioning.provisioning.infrastructure.persistence;

import com.nvan.telecomprovisioning.provisioning.domain.ServicePackage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServicePackageRepository extends JpaRepository<ServicePackage, Long> {

    List<ServicePackage> findAllByActiveTrueOrderByIdAsc();
}
