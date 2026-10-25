package com.nvan.telecomprovisioning.provisioning.application;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class SubscriptionService {

    private final SubscriptionTransactionService transactionService;

    public SubscriptionService(SubscriptionTransactionService transactionService) {
        this.transactionService = transactionService;
    }

    public SubscriptionView requestSubscription(String idempotencyKey, String customerRef, Long packageId) {
        String requestHash = hashRequest(customerRef, packageId);
        try {
            return transactionService.requestSubscription(
                    idempotencyKey, requestHash, customerRef, packageId);
        } catch (DataIntegrityViolationException exception) {
            var concurrentResult = transactionService.findByIdempotencyKey(idempotencyKey, requestHash);
            if (concurrentResult.isPresent()) {
                return concurrentResult.get();
            }
            throw exception;
        }
    }

    public SubscriptionView getSubscription(Long subscriptionId) {
        return transactionService.getSubscription(subscriptionId);
    }

    private String hashRequest(String customerRef, Long packageId) {
        String canonicalRequest = customerRef.length() + ":" + customerRef + ":" + packageId;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalRequest.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
