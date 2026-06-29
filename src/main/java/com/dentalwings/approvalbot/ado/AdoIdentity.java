package com.dentalwings.approvalbot.ado;

/**
 * Raw Azure DevOps identity data. Role matching and normalization belong to the workflow identity services, not this
 * transport boundary.
 */
public record AdoIdentity(String displayName, String emailOrLogin) {
}
