package com.nvan.telecomprovisioning.provisioning.api;

import com.nvan.telecomprovisioning.provisioning.application.SubscriptionService;
import com.nvan.telecomprovisioning.provisioning.application.SubscriptionView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/subscriptions")
@Validated
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping
    public ResponseEntity<SubscriptionResponse> requestSubscription(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody CreateSubscriptionRequest request) {
        SubscriptionView subscription = subscriptionService.requestSubscription(
                idempotencyKey,
                request.customerRef(),
                request.packageId());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(subscription.id())
                .toUri();
        return ResponseEntity.accepted()
                .location(location)
                .body(SubscriptionResponse.from(subscription));
    }

    @GetMapping("/{id}")
    public SubscriptionResponse getSubscription(@PathVariable Long id) {
        return SubscriptionResponse.from(subscriptionService.getSubscription(id));
    }
}
