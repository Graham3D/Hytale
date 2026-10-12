package com.inigmasgames.hywind;

/** R261 is the normal gameplay path; an explicit false restores R260 behavior. */
final class CycloneGateConfiguration {
    private CycloneGateConfiguration() { }

    static boolean enabled(String propertyName) {
        return enabledValue(System.getProperty(propertyName));
    }

    static boolean enabledValue(String configuredValue) {
        return configuredValue == null || !"false".equalsIgnoreCase(configuredValue.trim());
    }
}
