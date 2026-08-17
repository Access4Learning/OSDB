package org.sifunity.requestsconnector.auth;

/**
 * Cached credentials for a provider environment.
 */
public record ProviderCredentials(String sessionToken, String sharedSecret) {}
