package com.radix.regression;

import com.radixdlt.client.core.BootstrapConfig;
import com.radixdlt.client.core.network.websocket.WebSocketStatus;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.RadixApplicationAPI.Result;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.translate.data.DecryptedMessage;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.network.RadixNetworkState;
import io.reactivex.Observable;
import io.reactivex.functions.Predicate;
import io.reactivex.observers.TestObserver;

/**
 * RLAU-59
 */
public class UnsubscribeTest {
	private static final BootstrapConfig BOOTSTRAP_CONFIG;
	static {
		String bootstrapConfigName = System.getenv("RADIX_BOOTSTRAP_CONFIG");
		if (bootstrapConfigName != null) {
			BOOTSTRAP_CONFIG = Bootstrap.valueOf(bootstrapConfigName);
		} else {
			BOOTSTRAP_CONFIG = Bootstrap.LOCALHOST_SINGLENODE;
		}
	}

	private final static Predicate<RadixNetworkState> NETWORK_IS_CLOSE =
		state -> state.getNodes().entrySet().stream().noneMatch(e -> e.getValue().getStatus().equals(WebSocketStatus.CONNECTED));

	private final static Predicate<RadixNetworkState> NETWORK_IS_OPEN =
		state -> state.getNodes().entrySet().stream().anyMatch(e -> e.getValue().getStatus().equals(WebSocketStatus.CONNECTED));

	@Test
	public void given_i_am_a_library_user_with_no_connections__when_i_send_a_message_to_myself_to_completion__then_i_can_observe_all_network_connections_being_closed() {
		// Given I am a library user
		RadixApplicationAPI normalApi = RadixApplicationAPI.create(BOOTSTRAP_CONFIG, RadixIdentities.createNew());
		Observable<RadixNetworkState> networkStatus = normalApi
			.getNetworkState()
			.debounce(3, TimeUnit.SECONDS);

		TestObserver<RadixNetworkState> networkListener = TestObserver.create(Util.loggingObserver("NetworkListener"));
		networkStatus.takeUntil(NETWORK_IS_CLOSE).subscribe(networkListener);
		networkListener.awaitTerminalEvent();

		// When I send a message to myself to completion
		Result result = normalApi.sendMessage(new byte[] {0}, false);
		TestObserver<Object> completion = TestObserver.create(Util.loggingObserver("MessageSent"));
		result.toCompletable().subscribe(completion);
		completion.awaitTerminalEvent();


		// Then I can observe all network connections being closed
		TestObserver<RadixNetworkState> networkListener2 = TestObserver.create(Util.loggingObserver("NetworkListener2"));
		networkStatus.takeUntil(NETWORK_IS_CLOSE).subscribe(networkListener2);
		networkListener2.awaitTerminalEvent();
	}

	@Test
	public void given_i_am_connected_to_a_node_and_subscribed_once__when_i_dispose_of_the_lone_subscriber__then_i_can_observe_all_network_connections_being_closed() throws Exception {
		// Given I am connected to a node and listening to messages
		RadixApplicationAPI normalApi = RadixApplicationAPI.create(BOOTSTRAP_CONFIG, RadixIdentities.createNew());
		TestObserver<DecryptedMessage> messageListener = TestObserver.create(Util.loggingObserver("MessageListener"));
		normalApi.getMessages().subscribe(messageListener);
		Observable<RadixNetworkState> networkStatus = normalApi
			.getNetworkState()
			.debounce(3, TimeUnit.SECONDS);

		TestObserver<RadixNetworkState> networkListener = TestObserver.create(Util.loggingObserver("NetworkListener"));
		networkStatus.takeUntil(NETWORK_IS_OPEN).subscribe(networkListener);
		networkListener.awaitTerminalEvent();

		// When I dispose of the lone subscriber
		messageListener.dispose();

		// Then I can observe all network connections being closed
		TestObserver<RadixNetworkState> networkListener2 = TestObserver.create(Util.loggingObserver("NetworkListener2"));
		networkStatus.takeUntil(NETWORK_IS_CLOSE).subscribe(networkListener2);
		networkListener2.awaitTerminalEvent();
	}

	@Test
	public void given_i_am_connected_to_a_node_and_subscribed_twice__when_i_dispose_of_one_subscriber__then_i_can_observe_a_network_connection_still_open() {
		// Given I am connected to a node and listening to messages
		RadixApplicationAPI normalApi = RadixApplicationAPI.create(BOOTSTRAP_CONFIG, RadixIdentities.createNew());
		TestObserver<DecryptedMessage> messageListener1 = TestObserver.create(Util.loggingObserver("MessageListener1"));
		TestObserver<DecryptedMessage> messageListener2 = TestObserver.create(Util.loggingObserver("MessageListener2"));
		normalApi.getMessages().subscribe(messageListener1);
		normalApi.getMessages().subscribe(messageListener2);
		Observable<RadixNetworkState> networkStatus = normalApi
			.getNetworkState()
			.debounce(3, TimeUnit.SECONDS);
		TestObserver<RadixNetworkState> networkListener = TestObserver.create(Util.loggingObserver("NetworkListener"));
		networkStatus.takeUntil(NETWORK_IS_OPEN).subscribe(networkListener);
		networkListener.awaitTerminalEvent();

		// When I dispose of a subscriber
		messageListener1.dispose();

		// Then I can observe a network connection still open
		TestObserver<RadixNetworkState> networkListener2 = TestObserver.create(Util.loggingObserver("NetworkListener2"));
		networkStatus.takeUntil(NETWORK_IS_OPEN).subscribe(networkListener2);
		networkListener2.awaitTerminalEvent();
		messageListener2.dispose();
	}


}
