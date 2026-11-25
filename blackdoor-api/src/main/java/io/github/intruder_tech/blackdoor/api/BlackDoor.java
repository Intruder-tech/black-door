package io.github.intruder_tech.blackdoor.api;

/**
 * Entry point of the Black Door public API.
 *
 * <p>API v1 is under construction (see docs/DEVELOPER_API.md). In Phase 1 only version
 * information is exposed; nothing here is stable yet.
 */
public interface BlackDoor {

    /** Version of this API contract. Incremented according to docs/DEVELOPER_API.md section 3. */
    String API_VERSION = "0.1";

    /** Version of the running Black Door build, e.g. {@code 0.1.0-SNAPSHOT}. */
    String pluginVersion();

    /** Version of the API contract implemented by the running build. */
    default String apiVersion() {
        return API_VERSION;
    }
}
