package com.nvan.telecomprovisioning.provisioning.api;

import com.nvan.telecomprovisioning.provisioning.application.ServicePackageCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/service-packages")
public class ServicePackageController {

    private final ServicePackageCatalogService servicePackageCatalogService;

    public ServicePackageController(ServicePackageCatalogService servicePackageCatalogService) {
        this.servicePackageCatalogService = servicePackageCatalogService;
    }

    @GetMapping
    public List<ServicePackageResponse> listActivePackages() {
        return servicePackageCatalogService.listActivePackages()
                .stream()
                .map(ServicePackageResponse::from)
                .toList();
    }
}
