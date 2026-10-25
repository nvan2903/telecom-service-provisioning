package com.nvan.telecomprovisioning.provisioning.application;

public class ServicePackageUnavailableException extends RuntimeException {

    public ServicePackageUnavailableException(Long packageId) {
        super("Service package is unavailable: " + packageId);
    }
}
