package com.nvan.telecomprovisioning.provisioning.messaging;

import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class EventPayloadCodec {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public String encode(SubscriptionRequestedEvent event) {
        return jsonMapper.writeValueAsString(event);
    }

    public SubscriptionRequestedEvent decode(String payload) {
        SubscriptionRequestedEvent event =
                jsonMapper.readValue(payload, SubscriptionRequestedEvent.class);
        event.validate();
        return event;
    }
}
