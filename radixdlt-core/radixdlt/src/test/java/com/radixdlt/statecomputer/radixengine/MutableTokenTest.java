/*
 * (C) Copyright 2021 Radix DLT Ltd
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
 *
 */

package com.radixdlt.statecomputer.radixengine;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.name.Names;
import com.radixdlt.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.atom.MutableTokenDefinition;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.statecomputer.EpochCeilingView;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.utils.UInt256;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;


public class MutableTokenTest {
	private ECKeyPair keyPair = ECKeyPair.generateNew();
	private RadixAddress address = new RadixAddress((byte) 0, keyPair.getPublicKey());

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Inject
	private RadixEngine<LedgerAndBFTProof> sut;

	private Injector createInjector() {
		return Guice.createInjector(
			new SingleNodeAndPeersDeterministicNetworkModule(),
			new MockedGenesisModule(),
			new AbstractModule() {
				@Override
				protected void configure() {
					bindConstant().annotatedWith(Names.named("numPeers")).to(0);
					bind(MempoolConfig.class).toInstance(MempoolConfig.of(1000L, 10L));
					bindConstant().annotatedWith(DatabaseLocation.class).to(folder.getRoot().getAbsolutePath());
					bind(View.class).annotatedWith(EpochCeilingView.class).toInstance(View.of(100));
				}
			}
		);
	}

	@Test
	public void cannot_create_xrd_token() throws Exception {
		// Arrange
		createInjector().injectMembers(this);
		var tokDef = new MutableTokenDefinition(
			"XRD",
			"XRD",
			"XRD",
			null,
			null
		);
		var atom = TxBuilder.newBuilder(address)
			.createMutableToken(tokDef)
			.signAndBuild(keyPair::sign);

		// Act/Assert
		assertThatThrownBy(() -> sut.execute(List.of(atom))).isInstanceOf(RadixEngineException.class);
	}

	@Test
	public void atomic_token_creation_and_spend_should_succeed() throws Exception {
		// Arrange
		createInjector().injectMembers(this);
		var tokDef = new MutableTokenDefinition(
			"TEST",
			"test",
			"desc",
			null,
			null
		);
		var atom = TxBuilder.newBuilder(address)
			.createMutableToken(tokDef)
			.mint(RRI.of(address, "TEST"), address, UInt256.SEVEN)
			.transfer(RRI.of(address, "TEST"), address, UInt256.FIVE)
			.signAndBuild(keyPair::sign);

		// Act/Assert
		sut.execute(List.of(atom));
	}

	@Test
	public void can_create_no_description_token() throws TxBuilderException, RadixEngineException {
		// Arrange
		createInjector().injectMembers(this);
		var tokDef = new MutableTokenDefinition(
			"TEST",
			"test",
			null,
			null,
			null
		);
		var atom = TxBuilder.newBuilder(address)
			.createMutableToken(tokDef)
			.signAndBuild(keyPair::sign);

		// Act/Assert
		sut.execute(List.of(atom));
	}
}
