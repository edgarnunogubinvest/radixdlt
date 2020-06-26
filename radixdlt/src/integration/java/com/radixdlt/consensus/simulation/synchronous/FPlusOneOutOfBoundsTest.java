/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus.simulation.synchronous;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.radixdlt.consensus.simulation.BFTCheck.BFTCheckError;
import com.radixdlt.consensus.simulation.BFTSimulatedTest;
import com.radixdlt.consensus.simulation.BFTSimulatedTest.Builder;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class FPlusOneOutOfBoundsTest {
	private final int latency = 50;
	private final int synchronousTimeout = 8 * latency;
	private final int outOfBoundsLatency = synchronousTimeout;
	private final Builder bftTestBuilder = BFTSimulatedTest.builder()
		.pacemakerTimeout(2 * synchronousTimeout)
		.checkSafety("safety")
		.checkNoneCommitted("noneCommitted");

	/**
	 * Tests a configuration of 0 out of 3 nodes out of bounds
	 */
	@Test
	public void given_0_out_of_3_nodes_out_of_bounds() {
		BFTSimulatedTest test = bftTestBuilder
			.numNodesAndLatencies(3, latency, latency, latency)
			.build();

		Map<String, Optional<BFTCheckError>> results = test.run(1, TimeUnit.MINUTES);
		assertThat(results).hasEntrySatisfying("noneCommitted", error -> assertThat(error).isPresent());
	}

	/**
	 * Tests a configuration of 1 out of 3 nodes out of bounds
	 */
	@Test
	public void given_1_out_of_3_nodes_out_of_bounds() {
		BFTSimulatedTest test = bftTestBuilder
			.numNodesAndLatencies(3, latency, latency, outOfBoundsLatency)
			.build();

		Map<String, Optional<BFTCheckError>> results = test.run(1, TimeUnit.MINUTES);
		assertThat(results).allSatisfy((name, error) -> assertThat(error).isNotPresent());
	}
}
