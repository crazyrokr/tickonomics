package com.tickonomics.web.config;

import java.util.Arrays;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fails application startup when {@code security.auth-disabled=true} is active outside the local,
 * dev, or test profiles, so authentication can never be accidentally turned off in a deployed
 * environment. The check is an {@link ApplicationRunner} so a violation aborts context refresh.
 */
@Component
public class AuthDisabledStartupCheck implements ApplicationRunner {

    private static final Set<String> NON_AUTHED_PROFILES = Set.of("local", "dev", "test");

    private final Environment environment;
    private final SecurityProperties securityProperties;

    public AuthDisabledStartupCheck(Environment environment, SecurityProperties securityProperties) {
        this.environment = environment;
        this.securityProperties = securityProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (securityProperties.authDisabled() && !authDisabledAllowed()) {
            throw new IllegalStateException(
                    "security.auth-disabled=true is not permitted in non-local profiles: "
                            + Arrays.toString(environment.getActiveProfiles()));
        }
    }

    private boolean authDisabledAllowed() {
        String[] active = environment.getActiveProfiles();
        if (active.length == 0) {
            return false;
        }
        return Arrays.stream(active).anyMatch(NON_AUTHED_PROFILES::contains);
    }
}
