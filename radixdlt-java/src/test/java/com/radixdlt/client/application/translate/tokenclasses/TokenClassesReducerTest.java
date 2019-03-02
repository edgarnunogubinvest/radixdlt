package com.radixdlt.client.application.translate.tokenclasses;

import java.math.BigDecimal;
import java.util.Collections;

import com.radixdlt.client.atommodel.tokens.MintedTokensParticle;
import org.junit.Test;
import org.radix.utils.UInt256;

import com.radixdlt.client.application.translate.tokenclasses.TokenState.TokenSupplyType;
import com.radixdlt.client.atommodel.FungibleType;
import com.radixdlt.client.atommodel.tokens.OwnedTokensParticle;
import com.radixdlt.client.application.translate.tokens.TokenTypeReference;
import com.radixdlt.client.atommodel.tokens.TokenParticle;
import com.radixdlt.client.atommodel.tokens.TokenPermission;
import com.radixdlt.client.core.atoms.particles.SpunParticle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TokenClassesReducerTest {
	@Test
	public void testTokenWithNoMint() {
		TokenParticle tokenParticle = mock(TokenParticle.class);
		TokenTypeReference tokenRef = mock(TokenTypeReference.class);
		when(tokenParticle.getTokenClassReference()).thenReturn(tokenRef);
		when(tokenParticle.getName()).thenReturn("Name");
		when(tokenParticle.getSymbol()).thenReturn("ISO");
		when(tokenParticle.getDescription()).thenReturn("Desc");
		when(tokenParticle.getGranularity()).thenReturn(UInt256.ONE);
		when(tokenParticle.getTokenPermissions()).thenReturn(Collections.singletonMap(FungibleType.MINTED, TokenPermission.SAME_ATOM_ONLY));

		TokenClassesReducer tokenClassesReducer = new TokenClassesReducer();
		TokenClassesState state = tokenClassesReducer.reduce(TokenClassesState.init(), SpunParticle.up(tokenParticle));
		assertThat(state.getState().get(tokenRef)).isEqualTo(
			new TokenState("Name", "ISO", "Desc", BigDecimal.ZERO, TokenTypeReference.subunitsToUnits(1), TokenSupplyType.FIXED)
		);
	}

	@Test
	public void testTokenWithMint() {
		final UInt256 hundred = UInt256.TEN.pow(2);
		TokenParticle tokenParticle = mock(TokenParticle.class);
		TokenTypeReference tokenRef = mock(TokenTypeReference.class);
		when(tokenParticle.getTokenClassReference()).thenReturn(tokenRef);
		when(tokenParticle.getName()).thenReturn("Name");
		when(tokenParticle.getSymbol()).thenReturn("ISO");
		when(tokenParticle.getDescription()).thenReturn("Desc");
		when(tokenParticle.getGranularity()).thenReturn(UInt256.ONE);
		when(tokenParticle.getTokenPermissions()).thenReturn(Collections.singletonMap(FungibleType.MINTED, TokenPermission.TOKEN_OWNER_ONLY));

		MintedTokensParticle minted = mock(MintedTokensParticle.class);
		when(minted.getAmount()).thenReturn(hundred);
		when(minted.getType()).thenReturn(FungibleType.MINTED);
		when(minted.getTokenTypeReference()).thenReturn(tokenRef);

		TokenClassesReducer tokenClassesReducer = new TokenClassesReducer();
		TokenClassesState state1 = tokenClassesReducer.reduce(TokenClassesState.init(), SpunParticle.up(tokenParticle));
		TokenClassesState state2 = tokenClassesReducer.reduce(state1, SpunParticle.up(minted));
		assertThat(state2.getState().get(tokenRef)).isEqualTo(
			new TokenState(
				"Name",
				"ISO",
				"Desc",
				TokenTypeReference.subunitsToUnits(hundred),
				TokenTypeReference.subunitsToUnits(1),
				TokenSupplyType.MUTABLE
			)
		);
	}
}