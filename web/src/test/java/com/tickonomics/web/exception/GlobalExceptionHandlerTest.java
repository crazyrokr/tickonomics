package com.tickonomics.web.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    class ClientErrors {

        @Test
        void givenIllegalArgument_whenHandled_thenBadRequestProblemDetail() throws Exception {
            mockMvc.perform(get("/throw/bad"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Invalid request"))
                    .andExpect(jsonPath("$.detail").value("nope"));
        }
    }

    @Nested
    class ServerErrors {

        @Test
        void givenUnexpectedException_whenHandled_thenInternalServerErrorProblemDetail() throws Exception {
            mockMvc.perform(get("/throw/unexpected"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.title").value("Internal server error"))
                    .andExpect(jsonPath("$.detail").value(
                            "An unexpected error occurred. Contact support with the request timestamp."));
        }

        @Test
        void givenUnexpectedException_whenHandled_thenNoInternalDetailsInBody() throws Exception {
            String body = mockMvc.perform(get("/throw/unexpected"))
                    .andReturn().getResponse().getContentAsString();

            assertFalse(body.contains("internal leak"));
            assertFalse(body.contains("java.lang.RuntimeException"));
            assertFalse(body.contains("at com.tickonomics"));
        }
    }

    @RestController
    @RequestMapping("/throw")
    static class ThrowingController {

        @GetMapping("/bad")
        public void bad() {
            throw new IllegalArgumentException("nope");
        }

        @GetMapping("/unexpected")
        public void unexpected() {
            throw new RuntimeException("internal leak");
        }
    }
}
