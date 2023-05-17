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

The CircuitBreaker has a threshold set to 20% and permits a minimum number of call attempts. The cicuit breaker will then fail if the number of requests is above 5
and they are still failing.