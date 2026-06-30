// SPDX-License-Identifier: MIT
package org.sifunity.testprovider.controller;

import lombok.extern.slf4j.Slf4j;
import org.sifunity.testprovider.dto.ConfigureRequest;
import org.sifunity.testprovider.dto.PollResult;
import org.sifunity.testprovider.dto.ProviderConfigView;
import org.sifunity.testprovider.dto.ProviderStatusView;
import org.sifunity.testprovider.exception.ValidationException;
import org.sifunity.testprovider.service.DelayedPollRunner;
import org.sifunity.testprovider.service.RuntimeConfigService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Test-only, unauthenticated control surface for configuring, inspecting, and triggering the provider.
 *
 * <p>Mounted at {@code /test-control}. With the app's {@code server.servlet.context-path} of
 * {@code /sif/provider}, the effective paths are {@code /sif/provider/test-control/configure},
 * {@code /sif/provider/test-control/status}, and {@code /sif/provider/test-control/poll}.</p>
 *
 * <p>These endpoints require no authentication because the provider is a localhost test tool driven by the
 * Bruno certification suite (see the design's Security Considerations). Raw secrets are never returned:
 * {@code sessionToken} and {@code sharedSecret} are always masked on read paths. This controller must never
 * exist in a real or shipped provider.</p>
 */
@RestController
@RequestMapping("/test-control")
@Slf4j
public class TestControlController {

    private final RuntimeConfigService runtimeConfigService;
    private final DelayedPollRunner pollRunner;

    public TestControlController(RuntimeConfigService runtimeConfigService, DelayedPollRunner pollRunner) {
        this.runtimeConfigService = runtimeConfigService;
        this.pollRunner = pollRunner;
    }

    /**
     * Apply a runtime configure request.
     *
     * @param request the configure body (Spring guarantees a non-null deserialized record)
     * @return a masked {@link ProviderConfigView} with {@code configured = true} on success
     */
    @PostMapping("/configure")
    public ProviderConfigView configure(@RequestBody(required = false) ConfigureRequest request) {
        ConfigureRequest req = request != null
                ? request
                : new ConfigureRequest(null, null, null, null, null, null);
        return runtimeConfigService.configure(req);
    }

    /**
     * Inspect the live provider state without exposing raw secrets.
     *
     * @return a masked {@link ProviderStatusView}
     */
    @GetMapping("/status")
    public ProviderStatusView status() {
        return runtimeConfigService.getStatus();
    }

    /**
     * Trigger exactly one DELAYED poll cycle on demand.
     *
     * @return the {@link PollResult} describing whether a cycle ran and whether a message was processed
     */
    @PostMapping("/poll")
    public PollResult poll() {
        return pollRunner.triggerOnce();
    }

    /**
     * Translate a {@link ValidationException} into an HTTP 400 response identifying the offending field(s).
     *
     * @param ex the validation failure carrying per-field error descriptions
     * @return a {@code 400 Bad Request} with a body of the form {@code { "errors": { field: message } }}
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(ValidationException ex) {
        log.warn("Configure validation failed: {}", ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("errors", ex.getErrors());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
