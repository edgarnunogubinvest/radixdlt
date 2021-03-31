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
 */

package com.radixdlt.integration.mempool;

import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.application.NodeWalletModule;
import com.radixdlt.application.TokenUnitConversions;
import com.radixdlt.chaos.mempoolfiller.MempoolFillerModule;
import com.radixdlt.chaos.mempoolfiller.MempoolFillerUpdate;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.mempool.MempoolRelayTrigger;
import org.apache.logging.log4j.ThreadContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.radixdlt.CryptoModule;
import com.radixdlt.PersistedNodeForTestingModule;
import com.radixdlt.atom.Atom;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.safety.PersistentSafetyStateStore;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.environment.deterministic.ControlledSenderFactory;
import com.radixdlt.environment.deterministic.DeterministicEpochsConsensusProcessor;
import com.radixdlt.environment.deterministic.network.ControlledMessage;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;
import com.radixdlt.environment.deterministic.network.MessageMutator;
import com.radixdlt.environment.deterministic.network.MessageSelector;
import com.radixdlt.network.addressbook.PeersView;
import com.radixdlt.statecomputer.EpochCeilingView;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisAtomModule;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;
import com.radixdlt.utils.Base58;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import io.reactivex.rxjava3.schedulers.Timed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.radix.TokenIssuance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Full (non-validator) node should handle mempool changes correctly.
 */
@RunWith(Parameterized.class)
public class MempoolRelayTest {
	private static final int MEMPOOL_FILLER_NODE = 0;

	@Parameterized.Parameters
	public static Collection<Object[]> parameters() {
		return List.of(new Object[][]{
			{2, 1}, // 2 validators, 1 full node
			{5, 2} // 5 validators, 2 full nodes
		});
	}

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Inject
	@Genesis
	private List<Atom> genesisAtoms;

	private final ECKeyPair universeKey = ECKeyPair.generateNew();
	private final ImmutableList<Integer> validators;
	private final ImmutableList<Integer> fullNodes;

	private DeterministicNetwork network;
	private ImmutableList<Injector> nodes;

	public MempoolRelayTest(int numValidators, int numFullNodes) {
		this.validators = IntStream.range(0, numValidators)
			.boxed().collect(ImmutableList.toImmutableList());
		this.fullNodes = IntStream.range(numValidators, numValidators + numFullNodes)
			.boxed().collect(ImmutableList.toImmutableList());
	}

	@Before
	public void setup() {
		final var numNodes = this.validators.size() + this.fullNodes.size();

		final var nodeKeys = Stream.generate(ECKeyPair::generateNew)
			.limit(numNodes)
			.sorted(Comparator.comparing(k -> k.getPublicKey().euid()))
			.collect(ImmutableList.toImmutableList());

		final var bftNodes = nodeKeys.stream()
			.map(k -> BFTNode.create(k.getPublicKey())).collect(Collectors.toList());

		this.network = new DeterministicNetwork(
			bftNodes,
			MessageSelector.firstSelector(),
			MessageMutator.nothing()
		);

		this.injectGenesisAtomToThis(nodeKeys);

		this.nodes = nodeKeys.stream()
			.<Supplier<Injector>>map(k -> () -> createRunner(k, bftNodes))
			.map(Supplier::get)
			.collect(ImmutableList.toImmutableList());

		this.nodes.forEach(i -> i.getInstance(DeterministicEpochsConsensusProcessor.class).start());
	}

	private void injectGenesisAtomToThis(ImmutableList<ECKeyPair> nodeKeys) {
		/* Using injector to create a valid genesis atoms using the modules, and inject it to this instance */
		final var validatorsKeys = this.validators.stream().map(nodeKeys::get).collect(ImmutableList.toImmutableList());
		Guice.createInjector(
			new MockedGenesisAtomModule(),
			new CryptoModule(),
			new AbstractModule() {
				@Override
				public void configure() {
					bind(SystemCounters.class).toInstance(new SystemCountersImpl());
					bind(ECKeyPair.class).annotatedWith(Names.named("universeKey")).toInstance(universeKey);
					bind(new TypeLiteral<ImmutableList<ECKeyPair>>() { }).annotatedWith(Genesis.class).toInstance(validatorsKeys);
				}

				@ProvidesIntoSet
				private TokenIssuance mempoolFillerIssuance() {
					return TokenIssuance.of(
						nodeKeys.get(MEMPOOL_FILLER_NODE).getPublicKey(),
						TokenUnitConversions.unitsToSubunits(10000000000L)
					);
				}
			}
		).injectMembers(this);
	}

	private Injector createRunner(ECKeyPair ecKeyPair, List<BFTNode> allNodes) {
		return Guice.createInjector(
			new PersistedNodeForTestingModule(),
			new MempoolFillerModule(),
			new NodeWalletModule(),
			new AbstractModule() {
				@Override
				protected void configure() {
					bindConstant().annotatedWith(Names.named("magic")).to(0);
					bind(new TypeLiteral<List<Atom>>() { }).annotatedWith(Genesis.class).toInstance(genesisAtoms);
					bind(ECKeyPair.class).annotatedWith(Self.class).toInstance(ecKeyPair);
					bind(new TypeLiteral<List<BFTNode>>() { }).toInstance(allNodes);
					bind(PeersView.class).toInstance(() -> allNodes);
					bind(ControlledSenderFactory.class).toInstance(network::createSender);
					bind(View.class).annotatedWith(EpochCeilingView.class).toInstance(View.of(100));
					bind(MempoolConfig.class).toInstance(MempoolConfig.of(10L, 100L, 100L, 100L, 10));
					bindConstant().annotatedWith(DatabaseLocation.class)
						.to(folder.getRoot().getAbsolutePath() + "/" + Base58.toBase58(ecKeyPair.getPublicKey().getBytes()));
				}
			}
		);
	}

	@After
	public void teardown() {
		this.nodes.forEach(this::stopDatabase);
	}

	private void stopDatabase(Injector injector) {
		injector.getInstance(BerkeleyLedgerEntryStore.class).close();
		injector.getInstance(PersistentSafetyStateStore.class).close();
		injector.getInstance(DatabaseEnvironment.class).stop();
	}

	private void processForCount(int messageCount) {
		for (int i = 0; i < messageCount; i++) {
			processNext();
		}
	}

	private Timed<ControlledMessage> processNext() {
		final var msg = this.network.nextMessage();
		final var nodeIndex = msg.value().channelId().receiverIndex();
		final var injector = this.nodes.get(nodeIndex);
		withThreadCtx(injector, () ->
			injector.getInstance(DeterministicEpochsConsensusProcessor.class)
				.handleMessage(msg.value().origin(), msg.value().message())
		);
		return msg;
	}

	private void withThreadCtx(Injector injector, Runnable r) {
		ThreadContext.put("bftNode", " " + injector.getInstance(Key.get(BFTNode.class, Self.class)));
		try {
			r.run();
		} finally {
			ThreadContext.remove("bftNode");
		}
	}

	private long getCounter(int nodeIndex, CounterType counterType) {
		return this.nodes.get(nodeIndex).getInstance(SystemCounters.class).get(counterType);
	}

	private <T> void dispatch(int nodeIndex, Class<T> clazz, T event) {
		network.createSender(nodeIndex).getDispatcher(clazz).dispatch(event);
	}

	@Test
	public void full_node_should_relay_mempool_messages_so_they_can_be_processed_by_validator() {
		dispatch(0, MempoolFillerUpdate.class, MempoolFillerUpdate.enable(100, true));
		processForCount(40000);
		dispatch(0, MempoolFillerUpdate.class, MempoolFillerUpdate.disable());
		processForCount(10000);

		// assert that validators have an empty mempool, but not the full nodes
		this.validators.forEach(n -> assertEquals(0L, getCounter(n, CounterType.MEMPOOL_COUNT)));
		this.fullNodes.forEach(n -> assertTrue(getCounter(n, CounterType.MEMPOOL_COUNT) >= 1L));

		// trigger mempool relay on the full nodes and process some more messages
		this.fullNodes.forEach(n -> dispatch(n, MempoolRelayTrigger.class, MempoolRelayTrigger.create()));
		processForCount(10000);

		// at this point all mempools should be empty
		this.validators.forEach(n -> assertEquals(0L, getCounter(n, CounterType.MEMPOOL_COUNT)));
		this.fullNodes.forEach(n -> assertEquals(0L, getCounter(n, CounterType.MEMPOOL_COUNT)));
	}
}
