/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.circuitbreaker.resilience4j;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnErrorEvent;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnSuccessEvent;
import io.github.resilience4j.core.EventConsumer;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.mockito.Mock;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import reactor.test.StepVerifier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.circuitbreaker.commons.Customizer;
import org.springframework.cloud.circuitbreaker.commons.ReactiveCircuitBreakerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.times;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * @author Ryan Baxter
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT, classes = ReactiveResilience4JCircuitBreakerIntegrationTest.Application.class)
@DirtiesContext
public class ReactiveResilience4JCircuitBreakerIntegrationTest {
	@LocalServerPort
	int port = 0;

	@Mock
	static EventConsumer<CircuitBreakerOnErrorEvent> slowErrorConsumer;
	@Mock
	static EventConsumer<CircuitBreakerOnSuccessEvent> slowSuccessConsumer;
	@Mock
	static EventConsumer<CircuitBreakerOnErrorEvent> normalErrorConsumer;
	@Mock
	static EventConsumer<CircuitBreakerOnSuccessEvent> normalSuccessConsumer;
	@Mock
	static EventConsumer<CircuitBreakerOnErrorEvent> slowFluxErrorConsumer;
	@Mock
	static EventConsumer<CircuitBreakerOnSuccessEvent> slowFluxSuccessConsumer;
	@Mock
	static EventConsumer<CircuitBreakerOnErrorEvent> normalFluxErrorConsumer;
	@Mock
	static EventConsumer<CircuitBreakerOnSuccessEvent> normalFluxSuccessConsumer;

	@Configuration
	@EnableAutoConfiguration
	@RestController
	protected static class Application {
		@GetMapping("/slow")
		public Mono<String> slow() {
			return Mono.just("slow").delayElement(Duration.ofSeconds(3));
		}

		@GetMapping("/normal")
		public Mono<String> normal() {
			return Mono.just("normal");
		}

		@GetMapping("/slowflux")
		public Flux<String> slowFlux() {
			return Flux.just("slow", "flux").delayElements(Duration.ofSeconds(3));
		}

		@GetMapping("normalflux")
		public Flux<String> normalFlux() {
			return Flux.just("normal", "flux");
		}

		@Bean
		public Customizer<ReactiveResilience4JCircuitBreakerFactory> slowCusomtizer() {
			return factory -> {
				factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
						.circuitBreakerConfig(CircuitBreakerConfig.ofDefaults())
						.timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(4)).build()).build());
				factory.configure(builder -> builder
						.timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(2)).build())
						.circuitBreakerConfig(CircuitBreakerConfig.ofDefaults()), "slow", "slowflux");
				factory.addCircuitBreakerCustomizer(circuitBreaker ->
						circuitBreaker.getEventPublisher().onError(slowErrorConsumer).onSuccess(slowSuccessConsumer),
						"slow" );
				factory.addCircuitBreakerCustomizer(circuitBreaker -> circuitBreaker.getEventPublisher().onError(normalErrorConsumer).onSuccess(normalSuccessConsumer),
						"normal");
				factory.addCircuitBreakerCustomizer(circuitBreaker -> circuitBreaker.getEventPublisher().onError(slowFluxErrorConsumer).onSuccess(slowFluxSuccessConsumer),
						"slowflux");
				factory.addCircuitBreakerCustomizer(circuitBreaker -> circuitBreaker.getEventPublisher().onError(normalFluxErrorConsumer).onSuccess(normalFluxSuccessConsumer),
						"normalflux");
			};
		}

		@Service
		public static class DemoControllerService {
			private int port = 0;
			private ReactiveCircuitBreakerFactory cbFactory;


			public DemoControllerService(ReactiveCircuitBreakerFactory cbFactory) {
				this.cbFactory = cbFactory;
			}

			public Mono<String> slow() {
				return cbFactory.create("slow").run(WebClient.builder().baseUrl("http://localhost:" + port).build()
						.get().uri("/slow").retrieve().bodyToMono(String.class), t -> {
					t.printStackTrace();
					return Mono.just("fallback");
				});
			}

			public Mono<String> normal() {
				return cbFactory.create("normal").run(WebClient.builder().baseUrl("http://localhost:" + port).build()
						.get().uri("/normal").retrieve().bodyToMono(String.class), t -> {
					t.printStackTrace();
					return Mono.just("fallback");
				});
			}

			public Flux<String> slowFlux() {
				return cbFactory.create("slowflux").run(WebClient.builder().baseUrl("http://localhost:" + port).build()
						.get().uri("/slowflux").retrieve().bodyToFlux(new ParameterizedTypeReference<String>() { }), t -> {
					t.printStackTrace();
					return Flux.just("fluxfallback");
				});
			}

			public Flux<String> normalFlux() {
				return cbFactory.create("normalflux").run(WebClient.builder().baseUrl("http://localhost:" + port).build()
						.get().uri("/normalflux").retrieve().bodyToFlux(String.class), t -> {
					t.printStackTrace();
					return Flux.just("fluxfallback");
				});
			}

			public void setPort(int port) {
				this.port = port;
			}
		}
	}

	@Autowired
	ReactiveResilience4JCircuitBreakerIntegrationTest.Application.DemoControllerService service;

	@Before
	public void setup() {
		service.setPort(port);
	}

	@Test
	public void test() {
		assertEquals("normal", service.normal().block());
		verify(normalErrorConsumer, times(0)).consumeEvent(any());
		verify(normalSuccessConsumer, times(1)).consumeEvent(any());
		assertEquals("fallback", service.slow().block());
		verify(slowErrorConsumer, times(1)).consumeEvent(any());
		verify(slowSuccessConsumer, times(0)).consumeEvent(any());
		StepVerifier.create(service.normalFlux()).expectNext("normalflux").verifyComplete();
		verify(normalFluxErrorConsumer, times(0)).consumeEvent(any());
		verify(normalFluxSuccessConsumer, times(1)).consumeEvent(any());
		StepVerifier.create(service.slowFlux()).expectNext("fluxfallback").verifyComplete();
		verify(slowFluxErrorConsumer, times(1)).consumeEvent(any());
		verify(slowSuccessConsumer, times(0)).consumeEvent(any());
	}

}
