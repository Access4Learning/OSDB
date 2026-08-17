// SPDX-License-Identifier: MIT
package org.sifunity.integration.service;

import lombok.extern.slf4j.Slf4j;
import org.sifunity.integration.config.ServiceConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Starts a Spring Boot service as an external Java process using the executable JAR.
 * 
 * <p>This implementation uses ProcessBuilder to start services as separate processes,
 * which is more realistic and allows services to run independently with their own JVMs.
 * 
 * <p>Each service is started with test-specific configuration passed as system properties.
 * 
 * @since 1.0.0
 */
@Slf4j
public class EmbeddedServiceStarter implements ServiceStarter {
    
    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration HEALTH_CHECK_INTERVAL = Duration.ofSeconds(3);
    private static final int MAX_HEALTH_CHECK_ATTEMPTS = 40; // 2 minutes
    
    private final String serviceName;
    private Process process;
    private ServiceConfig config;
    private int actualPort;
    private Thread outputReader;
    private Thread errorReader;
    
    /**
     * Creates a new service starter for the specified service.
     * 
     * @param serviceName the service name (e.g., "environment-service")
     */
    public EmbeddedServiceStarter(String serviceName) {
        this.serviceName = serviceName;
    }
    
    @Override
    public void start(ServiceConfig config) {
        this.config = config;
        this.actualPort = config.getPort();
        
        log.info("Starting external service process: {}", serviceName);
        
        try {
            // Find the service JAR
            Path jarPath = findServiceJar(serviceName);
            log.info("Found service JAR: {}", jarPath);
            
            // Build command
            List<String> command = buildCommand(jarPath, config);
            log.info("Starting service with command: {}", String.join(" ", command));
            
            // Start process
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false);
            
            process = pb.start();
            
            // Start output readers
            startOutputReaders();
            
            log.info("Service process started: {} (PID: {})", serviceName, process.pid());
            
            // Wait for health check to pass
            waitForHealthy();
            
            log.info("Service is healthy: {}", serviceName);
            
        } catch (Exception e) {
            log.error("Failed to start service: {}", serviceName, e);
            stop(); // Clean up if startup failed
            throw new RuntimeException("Failed to start service: " + serviceName, e);
        }
    }
    
    @Override
    public void stop() {
        if (process != null && process.isAlive()) {
            log.info("Stopping external service process: {}", serviceName);
            
            try {
                // Try graceful shutdown first
                process.destroy();
                
                // Wait up to 10 seconds for graceful shutdown
                if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    log.warn("Service did not stop gracefully, forcing termination: {}", serviceName);
                    process.destroyForcibly();
                }
                
                log.info("Service stopped: {}", serviceName);
            } catch (Exception e) {
                log.error("Error stopping service: {}", serviceName, e);
                process.destroyForcibly();
            } finally {
                process = null;
            }
        }
        
        // Stop output readers
        if (outputReader != null) {
            outputReader.interrupt();
        }
        if (errorReader != null) {
            errorReader.interrupt();
        }
    }
    
    @Override
    public boolean isHealthy() {
        if (!isRunning()) {
            log.debug("Health check failed for {}: process not running", serviceName);
            return false;
        }
        
        try {
            String healthUrl = getBaseUrl() + "/actuator/health";
            log.debug("Checking health for {} at: {}", serviceName, healthUrl);
            
            URL url = new URL(healthUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000); // Increased from 2000
            conn.setReadTimeout(5000); // Increased from 2000
            
            int responseCode = conn.getResponseCode();
            log.debug("Health check for {} returned status: {}", serviceName, responseCode);
            conn.disconnect();
            
            return responseCode == 200;
            
        } catch (Exception e) {
            log.debug("Health check failed for {}: {} - {}", serviceName, e.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }
    
    @Override
    public int getPort() {
        if (!isRunning()) {
            throw new IllegalStateException("Service is not running: " + serviceName);
        }
        return actualPort;
    }
    
    @Override
    public String getBaseUrl() {
        if (!isRunning()) {
            throw new IllegalStateException("Service is not running: " + serviceName);
        }
        return "http://localhost:" + actualPort;
    }
    
    @Override
    public String getServiceName() {
        return serviceName;
    }
    
    @Override
    public boolean isRunning() {
        return process != null && process.isAlive();
    }
    
    /**
     * Finds the service JAR file in the local Maven repository.
     * 
     * @param serviceName the service name
     * @return the path to the JAR file
     * @throws IOException if the JAR cannot be found
     */
    private Path findServiceJar(String serviceName) throws IOException {
        // Get user home directory
        String userHome = System.getProperty("user.home");
        
        // Determine groupId and artifactId
        String groupId;
        String artifactId = serviceName;
        
        // All modules use org.sifunity.broker groupId
        groupId = "org/sifunity/broker";
        
        // Build path to JAR
        Path jarPath = Paths.get(userHome, ".m2", "repository", groupId, artifactId, 
            "1.0.0-SNAPSHOT", artifactId + "-1.0.0-SNAPSHOT.jar");
        
        if (!Files.exists(jarPath)) {
            throw new IOException("Service JAR not found: " + jarPath);
        }
        
        return jarPath;
    }
    
    /**
     * Builds the command to start the service.
     * 
     * @param jarPath the path to the service JAR
     * @param config the service configuration
     * @return the command as a list of strings
     */
    private List<String> buildCommand(Path jarPath, ServiceConfig config) {
        List<String> command = new ArrayList<>();
        
        // Java executable
        command.add("java");
        
        // JVM options
        command.add("-Xmx512m");
        command.add("-XX:+UseG1GC");
        
        // System properties from config
        Map<String, Object> props = config.toSpringProperties();
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            command.add("-D" + entry.getKey() + "=" + entry.getValue());
        }
        
        // Log all -D system properties for debugging connection issues
        log.info("[{}] System properties for child process:", serviceName);
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            String value = entry.getKey().toLowerCase().contains("password")
                    ? "****"
                    : String.valueOf(entry.getValue());
            log.info("[{}]   -D{}={}", serviceName, entry.getKey(), value);
        }
        
        // Test profile
        command.add("-Dspring.profiles.active=test");
        
        // Disable Flyway (migrations already applied)
        command.add("-Dspring.flyway.enabled=false");
        
        // Disable JPA schema validation (migrations already applied)
        command.add("-Dspring.jpa.hibernate.ddl-auto=none");
        
        // Enable actuator endpoints
        command.add("-Dmanagement.endpoints.web.exposure.include=health,info");
        command.add("-Dmanagement.endpoint.health.show-details=always");
        
        // Reduce logging noise
        command.add("-Dlogging.level.root=INFO"); // Changed from WARN to INFO
        command.add("-Dlogging.level.org.sifunity=INFO");
        command.add("-Dlogging.level.org.springframework.boot.web.embedded.tomcat=INFO"); // Add Tomcat logging
        
        // Disable observability health indicators (Jaeger/Logstash not available in test)
        command.add("-Dobservability.enabled=false");
        command.add("-Dobservability.tracing.enabled=false");
        
        // JAR file
        command.add("-jar");
        command.add(jarPath.toString());
        
        return command;
    }
    
    /**
     * Starts threads to read process output and error streams.
     */
    private void startOutputReaders() {
        // Create a file to capture full output for debugging
        Path outputFile = null;
        try {
            outputFile = Files.createTempFile(serviceName + "-", ".log");
            log.info("Capturing {} output to: {}", serviceName, outputFile);
        } catch (IOException e) {
            log.warn("Failed to create output file for {}", serviceName, e);
        }
        
        final Path finalOutputFile = outputFile;
        
        outputReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Log at INFO level so we can see startup output
                    log.info("[{} STDOUT] {}", serviceName, line);
                    
                    // Write to file if available
                    if (finalOutputFile != null) {
                        try {
                            Files.writeString(finalOutputFile, line + System.lineSeparator(), 
                                java.nio.file.StandardOpenOption.APPEND);
                        } catch (IOException e) {
                            // Ignore write errors
                        }
                    }
                    
                    // Try to extract the actual port from Tomcat/Netty startup message
                    // Example: "Tomcat started on port(s): 54321 (http)"
                    // Example: "Netty started on port 8080"
                    if (actualPort == 0 && (line.contains("Tomcat started on port") || line.contains("Netty started on port"))) {
                        try {
                            String portStr;
                            if (line.contains("Tomcat started on port")) {
                                portStr = line.substring(line.indexOf("port(s): ") + 9);
                                portStr = portStr.substring(0, portStr.indexOf(" "));
                            } else {
                                // Netty format: "Netty started on port 8080"
                                // Extract everything after "port " and trim
                                int portIndex = line.indexOf("port ");
                                if (portIndex >= 0) {
                                    portStr = line.substring(portIndex + 5).trim();
                                    // Remove any trailing text after the port number
                                    int spaceIndex = portStr.indexOf(" ");
                                    if (spaceIndex > 0) {
                                        portStr = portStr.substring(0, spaceIndex);
                                    }
                                } else {
                                    continue;
                                }
                            }
                            actualPort = Integer.parseInt(portStr);
                            log.info("Detected actual port for {}: {}", serviceName, actualPort);
                        } catch (Exception e) {
                            log.warn("Failed to parse port from line: {}", line, e);
                        }
                    }
                }
            } catch (IOException e) {
                if (!Thread.currentThread().isInterrupted()) {
                    log.error("Error reading output from {}", serviceName, e);
                }
            }
        });
        outputReader.setDaemon(true);
        outputReader.setName(serviceName + "-output");
        outputReader.start();
        
        errorReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Log at ERROR level so we definitely see errors
                    log.error("[{} STDERR] {}", serviceName, line);
                    
                    // Write to file if available
                    if (finalOutputFile != null) {
                        try {
                            Files.writeString(finalOutputFile, "[STDERR] " + line + System.lineSeparator(), 
                                java.nio.file.StandardOpenOption.APPEND);
                        } catch (IOException e) {
                            // Ignore write errors
                        }
                    }
                }
            } catch (IOException e) {
                if (!Thread.currentThread().isInterrupted()) {
                    log.error("Error reading error stream from {}", serviceName, e);
                }
            }
        });
        errorReader.setDaemon(true);
        errorReader.setName(serviceName + "-error");
        errorReader.start();
    }
    
    /**
     * Waits for the service to become healthy.
     * 
     * @throws RuntimeException if the service doesn't become healthy within the timeout
     */
    private void waitForHealthy() {
        log.info("Waiting for service to become healthy: {}", serviceName);
        
        // Give the service a moment to start and for port detection to happen
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        int attempts = 0;
        while (attempts < MAX_HEALTH_CHECK_ATTEMPTS) {
            if (!isRunning()) {
                // Process died - try to get exit code
                int exitCode = -1;
                try {
                    exitCode = process.exitValue();
                } catch (Exception e) {
                    // Ignore
                }
                
                // Give output readers a moment to flush
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                throw new RuntimeException(
                    String.format("Service process died during startup: %s (exit code: %d)", 
                        serviceName, exitCode)
                );
            }
            
            if (isHealthy()) {
                log.info("Service is healthy after {} attempts: {}", attempts + 1, serviceName);
                return;
            }
            
            attempts++;
            
            if (attempts < MAX_HEALTH_CHECK_ATTEMPTS) {
                try {
                    log.debug("Health check attempt {}/{} failed, retrying in {}ms", 
                        attempts, MAX_HEALTH_CHECK_ATTEMPTS, HEALTH_CHECK_INTERVAL.toMillis());
                    Thread.sleep(HEALTH_CHECK_INTERVAL.toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for service to become healthy", e);
                }
            }
        }
        
        throw new RuntimeException(
            String.format("Service did not become healthy within %d seconds: %s", 
                HEALTH_CHECK_INTERVAL.getSeconds() * MAX_HEALTH_CHECK_ATTEMPTS,
                serviceName)
        );
    }
}
