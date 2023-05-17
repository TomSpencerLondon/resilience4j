### Resilience4J overview
This link is useful for a starter on resilience4j:
https://www.baeldung.com/resilience4j

[Resilience4j](https://resilience4j.readme.io/docs) helps with implementing resilient systems by managing fault tolerance for remote communications.
It is inspired by [Hystrix](https://www.baeldung.com/introduction-to-hystrix) but offers a more convenient API and a Rate Limiter to block
to frequent requests. It also offers Bulkhead for too many concurrent requests.

We first add the dependency to our pom.xml:
```xml
    <dependencies>
        <dependency>
            <groupId>io.github.resilience4j</groupId>
            <artifactId>resilience4j-circuitbreaker</artifactId>
            <version>0.12.1</version>
        </dependency>
    </dependencies>
```
In this example we are going to use the circuitbreaker module.

### Circuit Breaker
The Circuit Breaker pattern helps us to prevent a cascade of failures when a remote service is down. This link is quite useful for understanding the circuitbreaker
pattern:
https://martinfowler.com/bliki/CircuitBreaker.html

Michael Nygard popularized the Circuit Breaker pattern in Release It. You wrap a protected function call in a circuit breaker object which monitors for failures.
Once the failures reach a certain threshold the circuit breaker trips and all further calls to the circuit breaker return with an error without the protected call
being made. The CircuitBreaker can be in one of the three states:
- CLOSED – everything is fine, no short-circuiting involved
- OPEN – remote server is down, all requests to it are short-circuited
- HALF_OPEN – a configured amount of time since entering OPEN state has elapsed and CircuitBreaker allows requests to check if the remote service is back online

This is our test for the CircuitBreaker:

```java
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
```

The CircuitBreaker has a threshold set to 20% and permits a minimum number of call attempts. The cicuit breaker will then fail if the number of requests is above 5,
and the requests are still failing.

### Bulkhead
We can also use a Bulkhead when we have too many concurrent requests. We add the dependency:
```xml
<!-- https://mvnrepository.com/artifact/io.github.resilience4j/resilience4j-bulkhead -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-bulkhead</artifactId>
    <version>2.0.2</version>
</dependency>
```

We can now add our test for our Bulkhead:

```java
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
}
```

The Bulkhead limits the number of concurrent calls to a particular service. For this we can configure:
- the max amount of parallel executions allowed by the bulkhead
- the max amount of time a thread will wait for when attempting to enter a saturated bulkhead

### Retry
For Retry we add the retry dependency:

```xml
<!-- https://mvnrepository.com/artifact/io.github.resilience4j/resilience4j-retry -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-retry</artifactId>
    <version>2.0.2</version>
</dependency>
```

We can now test retry to ensure it works as expected:
```java

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

    
}

```

### TimeLimiter
Finally we can use the TimeLimiter dependency:
```xml
<!-- https://mvnrepository.com/artifact/io.github.resilience4j/resilience4j-timelimiter -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-timelimiter</artifactId>
    <version>2.0.2</version>
</dependency>

```

We can then test this with:
```text
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
```

Resilience4J also offers an add-on for Dropwizard (resilience4j-metrics). Here we have looked at fault-tolerance with inter-server communication
and how Resilience4j can help us.