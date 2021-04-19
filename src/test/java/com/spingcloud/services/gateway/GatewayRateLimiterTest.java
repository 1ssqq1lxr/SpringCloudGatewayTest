package com.spingcloud.services.gateway;

import com.carrotsearch.junitbenchmarks.BenchmarkOptions;
import com.carrotsearch.junitbenchmarks.BenchmarkRule;
import org.junit.*;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MockServerContainer;
import com.spingcloud.services.gateway.model.Account;

import static org.mockserver.model.HttpResponse.response;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@RunWith(SpringRunner.class)
public class GatewayRateLimiterTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayRateLimiterTest.class);

    @Rule
    public TestRule benchmarkRun = new BenchmarkRule();

    @ClassRule
    public static MockServerContainer mockServer = new MockServerContainer();
    @ClassRule
    public static GenericContainer redis = new GenericContainer("redis:5.0.6").withExposedPorts(6379);

    @Autowired
    TestRestTemplate template;

    @BeforeClass
    public static void init() {

        System.setProperty("spring.cloud.gateway.routes[0].id", "account-service");
        System.setProperty("spring.cloud.gateway.routes[0].uri", "http://localhost:" + mockServer.getServerPort());
        System.setProperty("spring.cloud.gateway.routes[0].predicates[0]", "Path=/account/**");
        System.setProperty("spring.cloud.gateway.routes[0].filters[0]", "RewritePath=/account/(?<path>.*), /$\\{path}");
        System.setProperty("spring.cloud.gateway.routes[0].filters[1].name", "RequestRateLimiter");
        System.setProperty("spring.cloud.gateway.routes[0].filters[1].args.redis-rate-limiter.replenishRate", "10");
        System.setProperty("spring.cloud.gateway.routes[0].filters[1].args.redis-rate-limiter.burstCapacity", "20");
        System.setProperty("spring.redis.host", "localhost");
        System.setProperty("spring.redis.port", "" + redis.getMappedPort(6379));
        new MockServerClient(mockServer.getContainerIpAddress(), mockServer.getServerPort())
                .when(HttpRequest.request()
                        .withPath("/1"))
                .respond(response()
                        .withBody("{\"id\":1,\"number\":\"1234567890\"}")
                        .withHeader("Content-Type", "application/json"));
    }

    @Test
    @BenchmarkOptions(warmupRounds = 0, concurrency = 6, benchmarkRounds = 600)
    public void testAccountService() {
        ResponseEntity<Account> r = template.exchange("/account/{id}", HttpMethod.GET, null, Account.class, 1);
        LOGGER.info("Received: status->{}, payload->{}, remaining->{}", r.getStatusCodeValue(), r.getBody(), r.getHeaders().get("X-RateLimit-Remaining"));
//		Assert.assertEquals(200, r.getStatusCodeValue());
//		Assert.assertNotNull(r.getBody());
//		Assert.assertEquals(Integer.valueOf(1), r.getBody().getId());
//		Assert.assertEquals("1234567890", r.getBody().getNumber());
    }

}
