package com.tickonomics.web.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

class AuthDisabledStartupCheckTest {

    private AuthDisabledStartupCheck check(boolean authDisabled, String... profiles) {
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(profiles);
        return new AuthDisabledStartupCheck(env, new SecurityProperties(authDisabled, null, null, null, null, null));
    }

    @Nested
    class FailsWhenAuthDisabledOutsideLocalProfiles {

        @Test
        void givenAuthDisabledAndProdProfile_whenRun_thenThrows() {
            assertThrows(IllegalStateException.class, () -> check(true, "prod").run(null));
        }

        @Test
        void givenAuthDisabledAndNoProfile_whenRun_thenThrows() {
            assertThrows(IllegalStateException.class, () -> check(true).run(null));
        }
    }

    @Nested
    class AllowsAuthDisabledInLocalProfiles {

        @Test
        void givenAuthDisabledAndLocalProfile_whenRun_thenOk() {
            assertDoesNotThrow(() -> check(true, "local").run(null));
        }

        @Test
        void givenAuthDisabledAndDevProfile_whenRun_thenOk() {
            assertDoesNotThrow(() -> check(true, "dev").run(null));
        }

        @Test
        void givenAuthDisabledAndTestProfile_whenRun_thenOk() {
            assertDoesNotThrow(() -> check(true, "test").run(null));
        }
    }

    @Nested
    class AllowsAuthEnabledAnywhere {

        @Test
        void givenAuthEnabledAndProdProfile_whenRun_thenOk() {
            assertDoesNotThrow(() -> check(false, "prod").run(null));
        }

        @Test
        void givenAuthEnabledAndNoProfile_whenRun_thenOk() {
            assertDoesNotThrow(() -> check(false).run(null));
        }
    }
}
