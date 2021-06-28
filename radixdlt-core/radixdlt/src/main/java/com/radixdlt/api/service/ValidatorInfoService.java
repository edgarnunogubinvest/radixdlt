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

package com.radixdlt.api.service;

import com.google.inject.Inject;
import com.radixdlt.api.data.ValidatorInfoDetails;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.networks.Addressing;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.StakedValidators;
import com.radixdlt.utils.functional.FunctionalUtils;
import com.radixdlt.utils.functional.Result;
import com.radixdlt.utils.functional.Result.Mapper2;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.radixdlt.api.data.ApiErrors.UNKNOWN_VALIDATOR;
import static com.radixdlt.utils.functional.Tuple.tuple;

public class ValidatorInfoService {
	private final RadixEngine<LedgerAndBFTProof> radixEngine;
	private final Addressing addressing;

	@Inject
	public ValidatorInfoService(
		RadixEngine<LedgerAndBFTProof> radixEngine,
		Addressing addressing
	) {
		this.radixEngine = radixEngine;
		this.addressing = addressing;
	}

	public Mapper2<Optional<ECPublicKey>, List<ValidatorInfoDetails>> getValidators(
		int size, Optional<ECPublicKey> cursor
	) {
		var validators = radixEngine.getComputedState(StakedValidators.class);
		var result = validators.map(ValidatorInfoDetails::create);
		result.sort(Comparator.comparing(ValidatorInfoDetails::getTotalStake).reversed());

		var paged = cursor
			.map(key -> FunctionalUtils.skipUntil(result, v -> v.getValidatorKey().equals(key)))
			.orElse(result);

		var list = paged.stream().limit(size).collect(Collectors.toList());
		var newCursor = list.stream().reduce(FunctionalUtils::findLast).map(ValidatorInfoDetails::getValidatorKey);

		return () -> Result.ok(tuple(newCursor, list));
	}

	public List<ValidatorInfoDetails> getAllValidators() {
		var validators = radixEngine.getComputedState(StakedValidators.class);
		var result = validators.map(ValidatorInfoDetails::create);
		result.sort(Comparator.comparing(ValidatorInfoDetails::getTotalStake).reversed());

		return result;
	}

	public long getValidatorsCount() {
		return radixEngine.getComputedState(StakedValidators.class).count();
	}

	public Result<ValidatorInfoDetails> getValidator(ECPublicKey validatorPublicKey) {
		var validators = radixEngine.getComputedState(StakedValidators.class);

		return Result.fromOptional(
			UNKNOWN_VALIDATOR.with(addressing.forValidators().of(validatorPublicKey)),
			validators.mapSingle(validatorPublicKey, ValidatorInfoDetails::create)
		);
	}

}
