package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.PushSubscription;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {
    List<PushSubscription> findByUserId(Long userId);

    Optional<PushSubscription> findByEndpointHash(String endpointHash);
}
