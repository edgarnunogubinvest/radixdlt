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

package com.radixdlt.constraintmachine;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

public final class DownAllIndex {
	private final byte[] index;
	private final Class<? extends Particle> substateClass;

	public DownAllIndex(byte[] index, Class<? extends Particle> substateClass) {
		this.index = index;
		this.substateClass = substateClass;
	}

	public boolean test(RawSubstateBytes bytes) {
		return test(bytes.getData());
	}

	public boolean test(byte[] dataBytes) {
		if (dataBytes.length < index.length) {
			return false;
		}

		return Arrays.equals(dataBytes, 0, index.length, index, 0, index.length);
	}

	public boolean test(ByteBuffer buffer) {
		if (buffer.remaining() < index.length) {
			return false;
		}

		for (byte b : index) {
			if (buffer.get() != b) {
				return false;
			}
		}

		return true;
	}



	public byte[] getIndex() {
		return index;
	}

	public Class<? extends Particle> getSubstateClass() {
		return substateClass;
	}

	@Override
	public int hashCode() {
		return Objects.hash(index, substateClass);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof DownAllIndex)) {
			return false;
		}

		var other = (DownAllIndex) o;
		return this.index == other.index
			&& Objects.equals(this.substateClass, other.substateClass);
	}
}
