package com.reloop.repair.enums;

/**
 * Verification state of a repairer profile (repair-service's own copy -
 * services never share enum classes across deployments).
 */
public enum VerificationStatus {

    UNVERIFIED,
    VERIFIED,
    REJECTED

}
