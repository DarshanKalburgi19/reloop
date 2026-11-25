package com.reloop.product.client;

import java.util.Optional;

/**
 * product-service's view of repair-service, which owns repairer workshop profiles
 * (database-per-service: product-service never touches repair-service tables).
 */
public interface RepairerProfileClient {

    /**
     * Creates the workshop profile for a newly registered repairer account.
     *
     * @throws com.reloop.product.exception.RepairServiceException 503 if unreachable, 502 on an error answer
     */
    RepairerProfileView createProfile(CreateRepairerProfileCommand command);

    /** Best-effort read; empty when the profile is missing or repair-service is unreachable. */
    Optional<RepairerProfileView> findProfile(Long profileId);
}
