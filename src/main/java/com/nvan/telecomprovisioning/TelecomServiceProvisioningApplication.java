
package com.nvan.telecomprovisioning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TelecomServiceProvisioningApplication {

    public static void main(String[] args) {
        System.out.println("JVM TimeZone: "
                + java.util.TimeZone.getDefault().getID());

        SpringApplication.run(
                TelecomServiceProvisioningApplication.class, args);
    }
}
