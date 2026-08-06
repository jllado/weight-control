package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.PushSubscription;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {
    Optional<PushSubscription> findByEndpointHash(String endpointHash);
}
