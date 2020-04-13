/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package com.radixdlt.network;

import static org.mockito.Mockito.mock;

import com.radixdlt.consensus.ConsensusMessage;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.Vote;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;

public class TestEventCoordinatorNetworkTest {
	private static final int TEST_LOOPBACK_LATENCY = 50;

	@Test
	public void when_send_new_view_to_self__then_should_receive_it() {
		TestEventCoordinatorNetwork network = TestEventCoordinatorNetwork.orderedLatent(TEST_LOOPBACK_LATENCY);
		TestObserver<ConsensusMessage> testObserver = TestObserver.create();
		network.getNetworkRx(EUID.ONE).consensusMessages()
			.subscribe(testObserver);
		NewView newView = mock(NewView.class);
		network.getNetworkSender(EUID.ONE).sendNewView(newView, EUID.ONE);
		testObserver.awaitCount(1);
		testObserver.assertValue(newView);
	}

	@Test
	public void when_send_vote_to_self__then_should_receive_it() {
		TestEventCoordinatorNetwork network = TestEventCoordinatorNetwork.orderedLatent(TEST_LOOPBACK_LATENCY);
		TestObserver<ConsensusMessage> testObserver = TestObserver.create();
		network.getNetworkRx(EUID.ONE).consensusMessages()
			.subscribe(testObserver);
		Vote vote = mock(Vote.class);
		network.getNetworkSender(EUID.ONE).sendVote(vote, EUID.ONE);
		testObserver.awaitCount(1);
		testObserver.assertValue(vote);
	}

	@Test
	public void when_broadcast_proposal__then_should_receive_it() {
		TestEventCoordinatorNetwork network = TestEventCoordinatorNetwork.orderedLatent(TEST_LOOPBACK_LATENCY);
		TestObserver<ConsensusMessage> testObserver = TestObserver.create();
		network.getNetworkRx(EUID.ONE).consensusMessages()
			.subscribe(testObserver);
		Proposal proposal = mock(Proposal.class);
		network.getNetworkSender(EUID.ONE).broadcastProposal(proposal);
		testObserver.awaitCount(1);
		testObserver.assertValue(proposal);
	}

	@Test
	public void when_disable_and_then_send_new_view_to_self__then_should_receive_it() {
		TestEventCoordinatorNetwork network = TestEventCoordinatorNetwork.orderedLatent(TEST_LOOPBACK_LATENCY);
		TestObserver<ConsensusMessage> testObserver = TestObserver.create();
		network.setSendingDisable(EUID.ONE, true);
		network.getNetworkRx(EUID.ONE).consensusMessages()
			.subscribe(testObserver);
		NewView newView = mock(NewView.class);
		network.getNetworkSender(EUID.ONE).sendNewView(newView, EUID.ONE);
		testObserver.assertEmpty();
	}

	@Test
	public void when_disable_receive_and_other_sends_view_to_self__then_should_receive_it() {
		TestEventCoordinatorNetwork network = TestEventCoordinatorNetwork.orderedLatent(TEST_LOOPBACK_LATENCY);
		TestObserver<ConsensusMessage> testObserver = TestObserver.create();
		network.setReceivingDisable(EUID.ONE, true);
		network.getNetworkRx(EUID.ONE).consensusMessages()
			.subscribe(testObserver);
		NewView newView = mock(NewView.class);
		network.getNetworkSender(EUID.TWO).sendNewView(newView, EUID.ONE);
		testObserver.assertEmpty();
	}

	@Test
	public void when_disable_then_reenable_receive_and_other_sends_view_to_self__then_should_receive_it() {
		TestEventCoordinatorNetwork network = TestEventCoordinatorNetwork.orderedLatent(TEST_LOOPBACK_LATENCY);
		TestObserver<ConsensusMessage> testObserver = TestObserver.create();
		network.setReceivingDisable(EUID.ONE, true);
		network.setReceivingDisable(EUID.ONE, false);
		network.getNetworkRx(EUID.ONE).consensusMessages()
			.subscribe(testObserver);
		NewView newView = mock(NewView.class);
		network.getNetworkSender(EUID.TWO).sendNewView(newView, EUID.ONE);
		testObserver.awaitCount(1);
		testObserver.assertValue(newView);
	}
}