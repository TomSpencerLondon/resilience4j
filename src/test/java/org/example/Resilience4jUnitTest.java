package org.example;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.bulkhead.BulkheadConfig;

import java.time.Duration;
import java.util.function.Function;
import java.util.concurrent.*;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
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

    @Test
    void whenBulkheadIsUsed_thenItWorksAsExpected() throws Exception {
        BulkheadConfig config = BulkheadConfig.custom().maxConcurrentCalls(1).build();
        BulkheadRegistry registry = BulkheadRegistry.of(config);
        Bulkhead bulkhead = registry.bulkhead("my");
        Function<Integer, Integer> decorated = Bulkhead.decorateFunction(bulkhead, service::process);

        Future<?> taskInProgress = callAndBlock(decorated);

        try {
            assertThat(bulkhead.tryAcquirePermission()).isFalse();
        } finally {
            taskInProgress.cancel(true);
        }
    }

    private Future<?> callAndBlock(Function<Integer, Integer> decoratedService) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        when(service.process(anyInt())).thenAnswer(invocation -> {
            latch.countDown();
            Thread.currentThread().join();
            return null;
        });

        ForkJoinTask<?> result = ForkJoinPool.commonPool().submit(() -> {
            decoratedService.apply(1);
        });

        latch.await();
        return result;
    }

    @Test
    void whenRetryIsUsed_thenItWorksAsExpected() {
        RetryConfig config = RetryConfig.custom().maxAttempts(2).build();
        RetryRegistry registry = RetryRegistry.of(config);
        Retry retry = registry.retry("my");
        Function<Integer, Object> decorated = Retry.decorateFunction(retry, (Integer s) -> {
            service.process(s);
            return null;
        });

        when(service.process(anyInt())).thenThrow(new RuntimeException());

        try {
            decorated.apply(1);
            fail("Expected an exception to be thrown if all retries failed");
        } catch (Exception e) {
            verify(service, times(2)).process(any(Integer.class));
        }
    }

    @Test
    void whenTimeLimiterIsUsed_thenItWorksAsExpected() throws Exception {
        long ttl = 1;
        TimeLimiterConfig config = TimeLimiterConfig.custom().timeoutDuration(Duration.ofMillis(ttl)).build();
        TimeLimiter timeLimiter = TimeLimiter.of(config);

        Future futureMock = mock(Future.class);
        Callable restrictedCall = TimeLimiter.decorateFutureSupplier(timeLimiter, () -> futureMock);
        restrictedCall.call();

        verify(futureMock).get(ttl, TimeUnit.MILLISECONDS);
    }
}
