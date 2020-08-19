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

package com.radixdlt.consensus;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.bft.BFTNode;

/**
 * A distributed computer which manages the computed state in a BFT.
 */
public interface SyncedExecutor {
	/**
	 * Given a proposed vertex, executes prepare stage on
	 * the state computer, the result of which gets persisted on ledger.
	 *
	 * @param vertex the vertex to compute
	 * @return the results of executing the prepare stage
	 */
	PreparedCommand prepare(Vertex vertex);

	/**
	 * Syncs the computer to a target version given the target version
	 * and a list of peer targets
	 *
	 * @param vertexMetadata the target vertexMetadata
	 * @param target list of targets as hint of which peer has the state
	 * @param opaque some opaque client object which will be passed in a sync
	 * message if this returns false
	 * @return true if already synced, otherwise false
	 */
	boolean syncTo(VertexMetadata vertexMetadata, ImmutableList<BFTNode> target, Object opaque);

	/**
	 * Commit a command
	 * @param command the command to commit
	 * @param vertexMetadata ledger metadata regarding command
	 */
	void commit(Command command, VertexMetadata vertexMetadata);
}
