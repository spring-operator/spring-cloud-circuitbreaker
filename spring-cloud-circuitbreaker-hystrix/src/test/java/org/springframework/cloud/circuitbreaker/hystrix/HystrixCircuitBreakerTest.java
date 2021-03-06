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

package org.springframework.cloud.circuitbreaker.hystrix;

import org.junit.Test;
import org.springframework.cloud.circuitbreaker.commons.CircuitBreaker;

import static org.junit.Assert.assertEquals;


/**
 * @author Ryan Baxter
 */
public class HystrixCircuitBreakerTest {

	@Test
	public void run() {
		CircuitBreaker cb = new HystrixCircuitBreakerFactory().create("foo");
		String s = cb.run(() -> "foobar", t -> "fallback");
		assertEquals("foobar", cb.run(() -> "foobar", t -> "fallback"));
	}

	@Test
	public void fallback() {
		CircuitBreaker cb = new HystrixCircuitBreakerFactory().create("foo");
		assertEquals("fallback", cb.run(() -> {
			throw new RuntimeException("Boom");
		}, t -> "fallback"));
	}
}