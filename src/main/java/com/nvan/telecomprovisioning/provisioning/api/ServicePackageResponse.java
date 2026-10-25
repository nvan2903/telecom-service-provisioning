package com.nvan.telecomprovisioning.provisioning.api;

import com.nvan.telecomprovisioning.provisioning.domain.ServicePackage;

public record ServicePackageResponse(Long id, String code, String name) {

    public static ServicePackageResponse from(ServicePackage servicePackage) {
        return new ServicePackageResponse(
                servicePackage.getId(),
                servicePackage.getCode(),
                servicePackage.getName());
    }
}
