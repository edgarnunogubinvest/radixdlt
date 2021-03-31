/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.statecomputer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.consensus.Command;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineErrorCode;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.mempool.MempoolDuplicateException;
import com.radixdlt.mempool.MempoolFullException;
import com.radixdlt.mempool.MempoolAtom;
import com.radixdlt.mempool.MempoolRejectedException;
import com.radixdlt.atom.Atom;
import com.radixdlt.mempool.MempoolRelayCommands;
import com.radixdlt.mempool.MempoolRelayTrigger;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.utils.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A mempool which uses internal radix engine to be more efficient.
 */
@Singleton
public final class RadixEngineMempool implements Mempool<Atom> {

	private final ConcurrentHashMap<Command, MempoolAtom> data = new ConcurrentHashMap<>();
	private final Map<CMMicroInstruction, Set<Command>> particleIndex = new HashMap<>();
	private final MempoolConfig mempoolConfig;
	private final SystemCounters counters;
	private final RadixEngine<LedgerAndBFTProof> radixEngine;
	private final EventDispatcher<MempoolRelayCommands> mempoolRelayCommandsEventDispatcher;

	@Inject
	public RadixEngineMempool(
		RadixEngine<LedgerAndBFTProof> radixEngine,
		MempoolConfig mempoolConfig,
		SystemCounters counters,
		EventDispatcher<MempoolRelayCommands> mempoolRelayCommandsEventDispatcher
	) {
		if (mempoolConfig.maxSize() <= 0) {
			throw new IllegalArgumentException("mempool.maxSize must be positive: " + mempoolConfig.maxSize());
		}
		this.radixEngine = radixEngine;
		this.mempoolConfig = Objects.requireNonNull(mempoolConfig);
		this.counters = Objects.requireNonNull(counters);
		this.mempoolRelayCommandsEventDispatcher = Objects.requireNonNull(mempoolRelayCommandsEventDispatcher);
	}

	@Override
	public void add(Command command) throws MempoolRejectedException {
		Atom atom;
		try {
			atom = DefaultSerialization.getInstance().fromDson(command.getPayload(), Atom.class);
		} catch (DeserializeException e) {
			throw new MempoolRejectedException("Deserialize failure.");
		}

		if (this.data.size() >= this.mempoolConfig.maxSize()) {
			throw new MempoolFullException(
				String.format("Mempool full: %s of %s items", this.data.size(), this.mempoolConfig.maxSize())
			);
		}

		if (this.data.containsKey(command)) {
			throw new MempoolDuplicateException(String.format("Mempool already has command %s", command.getId()));
		}

		try {
			RadixEngine.RadixEngineBranch<LedgerAndBFTProof> checker = radixEngine.transientBranch();
			checker.execute(List.of(atom));
		} catch (RadixEngineException e) {
			// TODO: allow missing dependency atoms to live for a certain amount of time
			throw new RadixEngineMempoolException(e);
		} finally {
			radixEngine.deleteBranches();
		}

		this.data.put(command, MempoolAtom.create(atom, System.currentTimeMillis(), Optional.empty()));

		atom.uniqueInstructions()
			.forEach(i -> particleIndex.merge(i, Set.of(command), Sets::union));

		updateCounts();
	}

	@Override
	public List<Pair<Command, Exception>> committed(List<Atom> atoms) {
		final List<Pair<Command, Exception>> removed = new ArrayList<>();
		final Set<Atom> atomsSet = new HashSet<>(atoms);
		atoms.forEach(atom -> atom.uniqueInstructions()
			.flatMap(p -> {
				Set<Command> cmds = particleIndex.remove(p);
				return cmds != null ? cmds.stream() : Stream.empty();
			}).forEach(cmd -> {
				var toRemove = data.remove(cmd);
				// TODO: Cleanup
				if (toRemove != null && !atomsSet.contains(toRemove)) {
					removed.add(Pair.of(cmd, new RadixEngineMempoolException(
						new RadixEngineException(
							toRemove.getAtom(),
							RadixEngineErrorCode.CM_ERROR,
							"Mempool evicted",
							DataPointer.ofAtom()
						)
					)));
				}
			})
		);

		updateCounts();
		return removed;
	}

	// TODO: Order by highest fees paid
	@Override
	public List<Command> getCommands(int count, Set<Command> prepared) {
		var copy = new HashSet<>(data.keySet());
		prepared.stream()
			.map(data::get)
			.filter(Objects::nonNull)
			.map(MempoolAtom::getAtom)
			.flatMap(Atom::uniqueInstructions)
			.distinct()
			.flatMap(i -> particleIndex.getOrDefault(i, Set.of()).stream())
			.distinct()
			.forEach(copy::remove);

		return copy.stream().limit(count).collect(Collectors.toList());
	}

	public EventProcessor<MempoolRelayTrigger> mempoolRelayTriggerEventProcessor() {
		return ev -> {
			System.out.println("Mempool relay trigger!");
			final var now = System.currentTimeMillis();
			final var maxAddTime = now - this.mempoolConfig.commandRelayInitialDelay();
			final var commandsToRelay = this.data
				.entrySet().stream()
				.filter(e ->
					e.getValue().getInserted() <= maxAddTime
						&& now >= e.getValue().getLastRelayed().orElse(0L) + this.mempoolConfig.commandRelayRepeatDelay()
				)
				.map(e -> {
					final var updated = e.getValue().withLastRelayed(now);
					this.data.put(e.getKey(), updated);
					return e.getKey();
				})
				.collect(ImmutableList.toImmutableList());

			if (!commandsToRelay.isEmpty()) {
				mempoolRelayCommandsEventDispatcher.dispatch(MempoolRelayCommands.create(commandsToRelay));
			}
		};
	}

	private void updateCounts() {
		this.counters.set(SystemCounters.CounterType.MEMPOOL_COUNT, this.data.size());
		this.counters.set(SystemCounters.CounterType.MEMPOOL_MAXCOUNT, this.mempoolConfig.maxSize());
	}

	@Override
	public String toString() {
		return String.format("%s[%x:%s/%s]",
			getClass().getSimpleName(), System.identityHashCode(this), this.data.size(), this.mempoolConfig.maxSize());
	}
}
