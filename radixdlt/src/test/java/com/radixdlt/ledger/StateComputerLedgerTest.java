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

package com.radixdlt.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.PreparedCommand;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.crypto.Hash;
import com.radixdlt.statecomputer.RadixEngineStateComputer;
import com.radixdlt.ledger.StateComputerLedger.CommittedSender;
import com.radixdlt.ledger.StateComputerLedger.CommittedStateSyncSender;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.network.addressbook.Peer;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Test;

public class StateComputerLedgerTest {

	private Mempool mempool;
	private RadixEngineStateComputer executor;
	private StateComputerLedger stateComputerLedger;
	private CommittedStateSyncSender committedStateSyncSender;
	private CommittedSender committedSender;

	private SystemCounters counters;

	@Before
	public void setup() {
		this.mempool = mock(Mempool.class);
		// No type check issues with mocking generic here
		this.executor = mock(RadixEngineStateComputer.class);
		this.committedStateSyncSender = mock(CommittedStateSyncSender.class);
		this.counters = mock(SystemCounters.class);
		this.committedSender = mock(CommittedSender.class);

		this.stateComputerLedger = new StateComputerLedger(
			1233,
			mempool,
			executor,
			committedStateSyncSender,
			committedSender,
			counters
		);
	}

	/*
	@Test
	public void when_generate_proposal_with_empty_prepared__then_generate_proposal_should_return_atom() {
		ClientAtom atom = mock(ClientAtom.class);
		when(mempool.getAtoms(anyInt(), anySet())).thenReturn(Collections.singletonList(reAtom));
		Command command = syncExecutor.generateNextCommand(View.of(1), Collections.emptySet());
		AssertionsForClassTypes.assertThat(atom).isEqualTo(reAtom);
	}
	 */

	@Test
	public void when_prepare_with_command_and_not_end_of_epoch__then_should_return_next_state_version() {
		Vertex vertex = mock(Vertex.class);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(vertex.getQC()).thenReturn(qc);
		VertexMetadata parent = mock(VertexMetadata.class);
		when(parent.isEndOfEpoch()).thenReturn(false);
		when(parent.getStateVersion()).thenReturn(12345L);
		when(qc.getProposed()).thenReturn(parent);
		when(qc.getTimestampedSignatures()).thenReturn(new TimestampedECDSASignatures());
		when(vertex.getCommand()).thenReturn(mock(Command.class));

		PreparedCommand preparedCommand = stateComputerLedger.prepare(vertex);
		assertThat(preparedCommand.getNextValidatorSet()).isEmpty();
		assertThat(preparedCommand.getStateVersion()).isEqualTo(12346L);
	}

	@Test
	public void when_commit__then_correct_messages_are_sent() {
		Command command = mock(Command.class);
		Hash hash = mock(Hash.class);
		when(command.getHash()).thenReturn(hash);
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(vertexMetadata.getView()).then(i -> View.of(50));
		when(vertexMetadata.getStateVersion()).then(i -> 1234L);

		stateComputerLedger.commit(command, vertexMetadata);
		verify(executor, times(1)).commit(eq(command), eq(vertexMetadata));
		verify(mempool, times(1)).removeCommitted(eq(hash));
		verify(committedSender, times(1)).sendCommitted(any(), any());
	}

	@Test
	public void when_sync_to__will_complete_when_higher_or_equal_state_version() {
		Peer peer = mock(Peer.class);
		when(peer.hasSystem()).thenReturn(true);
		ECPublicKey pk = mock(ECPublicKey.class);
		EUID euid = mock(EUID.class);
		when(pk.euid()).thenReturn(euid);
		BFTNode node = mock(BFTNode.class);
		when(node.getKey()).thenReturn(pk);

		VertexMetadata nextVertexMetadata = mock(VertexMetadata.class);
		when(nextVertexMetadata.getStateVersion()).thenReturn(1234L);

		Runnable onSynced = mock(Runnable.class);
		Consumer<Long> onNotSynced = mock(Consumer.class);
		stateComputerLedger
			.ifCommitSynced(nextVertexMetadata)
			.then(onSynced)
			.elseExecuteAndSendMessageOnSync(onNotSynced, mock(Object.class));
		verify(committedStateSyncSender, never()).sendCommittedStateSync(anyLong(), any());
		verify(onSynced, never()).run();
		verify(onNotSynced, times(1)).accept(eq(1233L));

		stateComputerLedger.commit(mock(Command.class), nextVertexMetadata);

		verify(committedStateSyncSender, timeout(5000).atLeast(1)).sendCommittedStateSync(anyLong(), any());
	}
}