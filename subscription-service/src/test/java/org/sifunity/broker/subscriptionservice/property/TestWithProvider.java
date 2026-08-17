// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.property;

import net.jqwik.api.*;

class TestWithProvider {
    
    @Property(tries = 10)
    void testWithSimpleForAll(@ForAll String value) {
        // Should work with built-in arbitrary
        assert value != null || value == null; // Always true
    }
    
    @Property(tries = 10)
    void testWithCustomProvider(@ForAll("customStrings") String value) {
        // Should work with custom provider
        assert value.startsWith("test");
    }
    
    @Provide
    Arbitrary<String> customStrings() {
        return Arbitraries.just("test123");
    }
}
