// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.sifunity.broker.common.constant.EnvironmentType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity representing an environment in the SIF Unity Broker.
 * 
 * <p>An environment represents a registered application with its credentials,
 * configuration, and associated access control entries and provider registrations.</p>
 * 
 * <p>This entity owns the environments table and has cascade relationships
 * to ACL entries and provider registrations.</p>
 * 
 * @see org.sifunity.broker.common.constant.EnvironmentType
 */
@Entity
@Table(name = "environments")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Environment {
    
    /**
     * Unique identifier for the environment.
     * Generated as UUID.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    /**
     * Application name - descriptive label for the environment.
     * Per SIF spec, consumerName is advisory; no uniqueness constraint.
     */
    @Column(name = "application_name", nullable = false)
    private String applicationName;
    
    /**
     * Session token for authentication - must be unique across all environments.
     * Generated as UUID string during environment registration.
     * Used by applications to authenticate with the broker.
     */
    @Column(name = "session_token", nullable = false, unique = true)
    private String sessionToken;
    
    /**
     * Shared secret for HMAC signature computation.
     * Cryptographically random, minimum 256 bits (32 bytes).
     * Base64 encoded for storage.
     * Never exposed in queries - only returned during registration.
     */
    @Column(name = "shared_secret", nullable = false)
    private String sharedSecret;
    
    /**
     * Timestamp when the environment was created.
     * Set automatically during registration.
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    /**
     * Optional expiration timestamp for the environment.
     * If set, the environment credentials expire at this time.
     */
    @Column(name = "expires_at")
    private Instant expiresAt;
    
    /**
     * Default zone ID for this environment.
     * Used when no explicit zone is specified in requests.
     */
    @Column(name = "default_zone_id")
    private String defaultZoneId;
    
    /**
     * Type of environment (DIRECT or BROKERED).
     * Describes the communication topology per SIF 3.7.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private EnvironmentType type;
    
    /**
     * Solution UUID - references the parent solution's UUID primary key.
     * Used for database-enforced referential integrity via FK constraint.
     */
    @Column(name = "solution_uuid", nullable = false)
    private UUID solutionUuid;
    
    // --- SIF 3.7 fields (XSD environmentType) ---
    
    /**
     * Unique environment identifier that can be safely shared with others.
     * Must NOT match the environment's id, sessionToken, userToken, or applicationKey.
     * XSD: fingerprint (xs:token, optional)
     */
    @Column(name = "fingerprint")
    private String fingerprint;
    
    /**
     * Defines the way in which the applicationKey can be used to enforce security.
     * e.g., "Basic", "SIF_HMACSHA256"
     * XSD: authenticationMethod (xs:token, optional)
     */
    @Column(name = "authentication_method")
    private String authenticationMethod;
    
    /**
     * Distinguishes multiple instances of the same application.
     * XSD: instanceId (xs:token, optional)
     */
    @Column(name = "instance_id")
    private String instanceId;
    
    /**
     * Opaque user-level token for delegated authentication.
     * XSD: userToken (xs:string, optional)
     */
    @Column(name = "user_token")
    private String userToken;
    
    /**
     * A descriptive name for the application that will be readily identifiable
     * to Zone Administrators if it becomes a Registered Consumer.
     * XSD: consumerName (xs:string, optional)
     */
    @Column(name = "consumer_name")
    private String consumerName;
    
    // --- Flattened Application Info (XSD applicationInfoType) ---
    // 1:1 with environment, always read together, stored inline per legacy schema pattern
    
    /**
     * Opaque consumer authentication element. Site-specific content.
     * XSD: applicationInfo/applicationKey (xs:string, optional)
     */
    @Column(name = "appinfo_application_key")
    private String appinfoApplicationKey;
    
    /**
     * The version of the SIF infrastructure which the consumer supports.
     * XSD: applicationInfo/supportedInfrastructureVersion (xs:string, optional)
     */
    @Column(name = "appinfo_supported_infra_version")
    private String appinfoSupportedInfraVersion;
    
    /**
     * The namespace of the data payloads conveyed by this environment.
     * XSD: applicationInfo/dataModelNamespace (xs:anyURI, optional)
     */
    @Column(name = "appinfo_data_model_namespace")
    private String appinfoDataModelNamespace;
    
    /**
     * The transport which the consumer expects the infrastructure to use.
     * XSD: applicationInfo/transport (xs:string, optional)
     */
    @Column(name = "appinfo_transport")
    private String appinfoTransport;
    
    /**
     * Application product vendor name.
     * XSD: applicationInfo/applicationProduct/vendorName (xs:string, optional)
     */
    @Column(name = "appinfo_app_vendor_name")
    private String appinfoAppVendorName;
    
    /**
     * Application product name.
     * XSD: applicationInfo/applicationProduct/productName (xs:string, mandatory in productIdentityType)
     */
    @Column(name = "appinfo_app_product_name")
    private String appinfoAppProductName;
    
    /**
     * Application product version.
     * XSD: applicationInfo/applicationProduct/productVersion (xs:string, optional)
     */
    @Column(name = "appinfo_app_product_version")
    private String appinfoAppProductVersion;
    
    /**
     * Application product icon URI.
     * XSD: applicationInfo/applicationProduct/iconURI (xs:anyURI, optional)
     */
    @Column(name = "appinfo_app_icon_uri")
    private String appinfoAppIconUri;
    
    /**
     * Adapter product vendor name.
     * XSD: applicationInfo/adapterProduct/vendorName (xs:string, optional)
     */
    @Column(name = "appinfo_adapter_vendor_name")
    private String appinfoAdapterVendorName;
    
    /**
     * Adapter product name.
     * XSD: applicationInfo/adapterProduct/productName (xs:string, mandatory in productIdentityType)
     */
    @Column(name = "appinfo_adapter_product_name")
    private String appinfoAdapterProductName;
    
    /**
     * Adapter product version.
     * XSD: applicationInfo/adapterProduct/productVersion (xs:string, optional)
     */
    @Column(name = "appinfo_adapter_product_version")
    private String appinfoAdapterProductVersion;
    
    /**
     * Adapter product icon URI.
     * XSD: applicationInfo/adapterProduct/iconURI (xs:anyURI, optional)
     */
    @Column(name = "appinfo_adapter_icon_uri")
    private String appinfoAdapterIconUri;
    
    /**
     * ACL entries associated with this environment.
     * Cascade delete - when environment is deleted, all ACL entries are deleted.
     * Orphan removal - if ACL entry is removed from collection, it's deleted.
     */
    @OneToMany(mappedBy = "environment", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AclEntry> aclEntries = new ArrayList<>();
    
    /**
     * Provider registrations associated with this environment.
     * Cascade delete - when environment is deleted, all provider registrations are deleted.
     * Orphan removal - if provider registration is removed from collection, it's deleted.
     */
    @OneToMany(mappedBy = "environment", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProviderRegistration> providerRegistrations = new ArrayList<>();
    
    /**
     * Sets the created timestamp to current time.
     * Called automatically before persisting new entities.
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
    
    /**
     * Adds an ACL entry to this environment.
     * Maintains bidirectional relationship.
     * 
     * @param aclEntry the ACL entry to add
     */
    public void addAclEntry(AclEntry aclEntry) {
        aclEntries.add(aclEntry);
        aclEntry.setEnvironment(this);
    }
    
    /**
     * Removes an ACL entry from this environment.
     * Maintains bidirectional relationship.
     * 
     * @param aclEntry the ACL entry to remove
     */
    public void removeAclEntry(AclEntry aclEntry) {
        aclEntries.remove(aclEntry);
        aclEntry.setEnvironment(null);
    }
    
    /**
     * Adds a provider registration to this environment.
     * Maintains bidirectional relationship.
     * 
     * @param providerRegistration the provider registration to add
     */
    public void addProviderRegistration(ProviderRegistration providerRegistration) {
        providerRegistrations.add(providerRegistration);
        providerRegistration.setEnvironment(this);
    }
    
    /**
     * Removes a provider registration from this environment.
     * Maintains bidirectional relationship.
     * 
     * @param providerRegistration the provider registration to remove
     */
    public void removeProviderRegistration(ProviderRegistration providerRegistration) {
        providerRegistrations.remove(providerRegistration);
        providerRegistration.setEnvironment(null);
    }
    
    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public String getApplicationName() { return applicationName; }
    public void setApplicationName(String applicationName) { this.applicationName = applicationName; }
    
    public String getSessionToken() { return sessionToken; }
    public void setSessionToken(String sessionToken) { this.sessionToken = sessionToken; }
    
    public String getSharedSecret() { return sharedSecret; }
    public void setSharedSecret(String sharedSecret) { this.sharedSecret = sharedSecret; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    
    public String getDefaultZoneId() { return defaultZoneId; }
    public void setDefaultZoneId(String defaultZoneId) { this.defaultZoneId = defaultZoneId; }
    
    public EnvironmentType getType() { return type; }
    public void setType(EnvironmentType type) { this.type = type; }
    
    public UUID getSolutionUuid() { return solutionUuid; }
    public void setSolutionUuid(UUID solutionUuid) { this.solutionUuid = solutionUuid; }
    
    public List<AclEntry> getAclEntries() { return aclEntries; }
    public void setAclEntries(List<AclEntry> aclEntries) { this.aclEntries = aclEntries; }
    
    public List<ProviderRegistration> getProviderRegistrations() { return providerRegistrations; }
    public void setProviderRegistrations(List<ProviderRegistration> providerRegistrations) { this.providerRegistrations = providerRegistrations; }
    
    // --- SIF 3.7 field getters/setters ---
    
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }
    
    public String getAuthenticationMethod() { return authenticationMethod; }
    public void setAuthenticationMethod(String authenticationMethod) { this.authenticationMethod = authenticationMethod; }
    
    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
    
    public String getUserToken() { return userToken; }
    public void setUserToken(String userToken) { this.userToken = userToken; }
    
    public String getConsumerName() { return consumerName; }
    public void setConsumerName(String consumerName) { this.consumerName = consumerName; }
    
    // --- Flattened Application Info getters/setters ---
    
    public String getAppinfoApplicationKey() { return appinfoApplicationKey; }
    public void setAppinfoApplicationKey(String appinfoApplicationKey) { this.appinfoApplicationKey = appinfoApplicationKey; }
    
    public String getAppinfoSupportedInfraVersion() { return appinfoSupportedInfraVersion; }
    public void setAppinfoSupportedInfraVersion(String appinfoSupportedInfraVersion) { this.appinfoSupportedInfraVersion = appinfoSupportedInfraVersion; }
    
    public String getAppinfoDataModelNamespace() { return appinfoDataModelNamespace; }
    public void setAppinfoDataModelNamespace(String appinfoDataModelNamespace) { this.appinfoDataModelNamespace = appinfoDataModelNamespace; }
    
    public String getAppinfoTransport() { return appinfoTransport; }
    public void setAppinfoTransport(String appinfoTransport) { this.appinfoTransport = appinfoTransport; }
    
    public String getAppinfoAppVendorName() { return appinfoAppVendorName; }
    public void setAppinfoAppVendorName(String appinfoAppVendorName) { this.appinfoAppVendorName = appinfoAppVendorName; }
    
    public String getAppinfoAppProductName() { return appinfoAppProductName; }
    public void setAppinfoAppProductName(String appinfoAppProductName) { this.appinfoAppProductName = appinfoAppProductName; }
    
    public String getAppinfoAppProductVersion() { return appinfoAppProductVersion; }
    public void setAppinfoAppProductVersion(String appinfoAppProductVersion) { this.appinfoAppProductVersion = appinfoAppProductVersion; }
    
    public String getAppinfoAppIconUri() { return appinfoAppIconUri; }
    public void setAppinfoAppIconUri(String appinfoAppIconUri) { this.appinfoAppIconUri = appinfoAppIconUri; }
    
    public String getAppinfoAdapterVendorName() { return appinfoAdapterVendorName; }
    public void setAppinfoAdapterVendorName(String appinfoAdapterVendorName) { this.appinfoAdapterVendorName = appinfoAdapterVendorName; }
    
    public String getAppinfoAdapterProductName() { return appinfoAdapterProductName; }
    public void setAppinfoAdapterProductName(String appinfoAdapterProductName) { this.appinfoAdapterProductName = appinfoAdapterProductName; }
    
    public String getAppinfoAdapterProductVersion() { return appinfoAdapterProductVersion; }
    public void setAppinfoAdapterProductVersion(String appinfoAdapterProductVersion) { this.appinfoAdapterProductVersion = appinfoAdapterProductVersion; }
    
    public String getAppinfoAdapterIconUri() { return appinfoAdapterIconUri; }
    public void setAppinfoAdapterIconUri(String appinfoAdapterIconUri) { this.appinfoAdapterIconUri = appinfoAdapterIconUri; }
}