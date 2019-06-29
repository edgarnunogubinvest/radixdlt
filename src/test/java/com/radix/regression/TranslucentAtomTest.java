package com.radix.regression;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.RadixApplicationAPI.Result;
import com.radixdlt.client.application.identity.LocalRadixIdentity;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.atommodel.message.MessageParticle;
import com.radixdlt.client.atommodel.message.MetadataMap;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.BootstrapConfig;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.UnsignedAtom;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.core.network.actions.SubmitAtomAction;
import com.radixdlt.client.core.network.actions.SubmitAtomStatusAction;
import io.reactivex.observers.TestObserver;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.radix.common.ID.EUID;

public class TranslucentAtomTest {
	private static final BootstrapConfig BOOTSTRAP_CONFIG;
	static {
		String bootstrapConfigName = System.getenv("RADIX_BOOTSTRAP_CONFIG");
		if (bootstrapConfigName != null) {
			BOOTSTRAP_CONFIG = Bootstrap.valueOf(bootstrapConfigName);
		} else {
			BOOTSTRAP_CONFIG = Bootstrap.LOCALHOST_SINGLENODE;
		}
	}

	private RadixApplicationAPI api;
	private LocalRadixIdentity identity;

	@Before
	public void setUp() {
		this.identity = RadixIdentities.createNew();
		this.api = RadixApplicationAPI.create(BOOTSTRAP_CONFIG, identity);
	}

	@Test
	public void testWrongDestination() {
		MessageParticle messageParticle = new MessageParticle(
			api.getMyAddress(),
			api.getMyAddress(),
			new byte[1],
			new MetadataMap(),
			0, ImmutableSet.of(EUID.ONE));
		UnsignedAtom unsignedAtom = new UnsignedAtom(new Atom(ParticleGroup.of(SpunParticle.up(messageParticle)), System.currentTimeMillis()));
		Atom signedAtom = this.identity.syncSign(unsignedAtom);
		Result result = api.submitAtom(signedAtom);
		TestObserver<SubmitAtomAction> testObserver = TestObserver.create(Util.loggingObserver("SubmitAtom"));
		result.toObservable()
			.subscribe(testObserver);
		testObserver.awaitTerminalEvent(5, TimeUnit.SECONDS);
		testObserver.assertNoErrors();
		testObserver.assertComplete();
		testObserver.assertValueAt(3, action -> {
			SubmitAtomStatusAction res = (SubmitAtomStatusAction) action;
			return res.getStatusNotification().getAtomStatus() == AtomStatus.EVICTED_FAILED_CM_VERIFICATION;
		});
	}

	@Test
	public void testExtraDestination() {
		MessageParticle messageParticle = new MessageParticle(
			api.getMyAddress(),
			api.getMyAddress(),
			new byte[1],
			new MetadataMap(),
			0, ImmutableSet.of(api.getMyAddress().getUID(), EUID.ONE));
		UnsignedAtom unsignedAtom = new UnsignedAtom(new Atom(ParticleGroup.of(SpunParticle.up(messageParticle)), System.currentTimeMillis()));
		Atom signedAtom = this.identity.syncSign(unsignedAtom);
		Result result = api.submitAtom(signedAtom);
		TestObserver<SubmitAtomAction> testObserver = TestObserver.create(Util.loggingObserver("SubmitAtom"));
		result.toObservable()
			.subscribe(testObserver);
		testObserver.awaitTerminalEvent(30, TimeUnit.SECONDS);
		testObserver.assertNoErrors();
		testObserver.assertComplete();
		testObserver.assertValueAt(3, action -> {
			SubmitAtomStatusAction res = (SubmitAtomStatusAction) action;
			return res.getStatusNotification().getAtomStatus() == AtomStatus.EVICTED_FAILED_CM_VERIFICATION;
		});
	}
}
