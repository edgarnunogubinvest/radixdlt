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

package com.radixdlt.fees;

import java.util.Set;

import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.utils.UInt256;

/**
 * Fee entry.
 */
public interface FeeEntry {
	/**
	 * Compute the fee for the specified atom with the specified outputs.
	 *
	 * @param atom The atom to compute the partial fee for
	 * @param feeSize The size of the atom for fee calculation purposes
	 * @param outputs The atom's output particles
	 * @return The fee
	 */
	UInt256 feeFor(Atom atom, int feeSize, Set<Particle> outputs);
}
