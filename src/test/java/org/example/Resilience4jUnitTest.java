package org.example;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

public class Resilience4jUnitTest {

    interface RemoteService {
        int process(int i);
    }



    private RemoteService service;

    @BeforeEach
    void setUp() {
        service = mock(RemoteService.class);
    }

    @Test
    void whenCircuitBreakerIsUser_thenItWorksAsExpected() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                // Percentage of failures to start short-circuit
                .failureRateThreshold(20)
                // Min number of call attempts
                .ringBufferSizeInClosedState(5)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        CircuitBreaker circuitBreaker = registry.circuitBreaker("my");
        Function<Integer, Integer> decorated = CircuitBreaker.decorateFunction(circuitBreaker, service::process);

        when(service.process(anyInt())).thenThrow(new RuntimeException());

        for (int i = 0; i < 10; i++) {
            try {
                decorated.apply(i);
            } catch (Exception ignore) {

            }
        }

        verify(service, times(5)).process(any(Integer.class));
    }
}
