package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.domain.PushSubscription;

public interface PushGateway {
    int send(PushSubscription subscription, String payload, int ttlSeconds);
}
