package com.nvan.telecomprovisioning.provisioning.application;

import com.nvan.telecomprovisioning.provisioning.domain.ServicePackage;
import com.nvan.telecomprovisioning.provisioning.infrastructure.persistence.ServicePackageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ServicePackageCatalogService {

    private final ServicePackageRepository servicePackageRepository;

    public ServicePackageCatalogService(ServicePackageRepository servicePackageRepository) {
        this.servicePackageRepository = servicePackageRepository;
    }

    @Transactional(readOnly = true)
    public List<ServicePackage> listActivePackages() {
        return servicePackageRepository.findAllByActiveTrueOrderByIdAsc();
    }
}
