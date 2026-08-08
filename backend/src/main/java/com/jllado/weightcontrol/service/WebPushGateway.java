package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.domain.PushSubscription;
import java.security.GeneralSecurityException;
import java.security.Security;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Component;

@Component
public class WebPushGateway implements PushGateway {

    private final AppProperties properties;

    public WebPushGateway(AppProperties properties) {
        this.properties = properties;
        Security.addProvider(new BouncyCastleProvider());
    }

    @Override
    public int send(PushSubscription subscription, String payload, int ttlSeconds) {
        try {
            PushService service = new PushService(
                properties.push().publicKey(),
                properties.push().privateKey(),
                properties.push().subject()
            );
            Notification notification = new Notification(
                subscription.getEndpoint(),
                subscription.getP256dh(),
                subscription.getAuth(),
                payload.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                ttlSeconds
            );
            return service.send(notification, Encoding.AES128GCM).getStatusLine().getStatusCode();
        } catch (GeneralSecurityException | java.io.IOException | org.jose4j.lang.JoseException | java.util.concurrent.ExecutionException e) {
            throw new PushDeliveryException("Push notification delivery failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PushDeliveryException("Push notification delivery was interrupted", e);
        }
    }
}
