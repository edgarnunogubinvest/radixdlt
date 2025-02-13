/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.harness.simulation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.RateLimiter;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Modules;
import com.radixdlt.ConsensusRecoveryModule;
import com.radixdlt.FunctionalNodeModule;
import com.radixdlt.LedgerRecoveryModule;
import com.radixdlt.MockedCryptoModule;
import com.radixdlt.MockedKeyModule;
import com.radixdlt.MockedPersistenceStoreModule;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.PacemakerMaxExponent;
import com.radixdlt.consensus.bft.PacemakerRate;
import com.radixdlt.consensus.bft.PacemakerTimeout;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.sync.BFTSyncPatienceMillis;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.environment.rx.RxEnvironmentModule;
import com.radixdlt.harness.MockedPeersViewModule;
import com.radixdlt.harness.simulation.TestInvariant.TestInvariantError;
import com.radixdlt.harness.simulation.application.BFTValidatorSetNodeSelector;
import com.radixdlt.harness.simulation.application.EpochsNodeSelector;
import com.radixdlt.harness.simulation.application.LocalMempoolPeriodicSubmitter;
import com.radixdlt.harness.simulation.application.NodeSelector;
import com.radixdlt.harness.simulation.application.TxnGenerator;
import com.radixdlt.harness.simulation.monitors.NodeEvents;
import com.radixdlt.harness.simulation.monitors.SimulationNodeEventsModule;
import com.radixdlt.harness.simulation.network.SimulationNetwork;
import com.radixdlt.harness.simulation.network.SimulationNodes;
import com.radixdlt.ledger.DtoLedgerProof;
import com.radixdlt.ledger.LedgerAccumulator;
import com.radixdlt.ledger.SimpleLedgerAccumulatorAndVerifier;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.middleware2.network.GetVerticesRequestRateLimit;
import com.radixdlt.network.p2p.NoOpPeerControl;
import com.radixdlt.network.p2p.PeerControl;
import com.radixdlt.networks.Addressing;
import com.radixdlt.networks.Network;
import com.radixdlt.recovery.MockedRecoveryModule;
import com.radixdlt.statecomputer.EpochCeilingView;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.RadixEngineModule;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import com.radixdlt.store.MockedRadixEngineStoreModule;
import com.radixdlt.sync.CommittedReader;
import com.radixdlt.sync.MockedCommittedReaderModule;
import com.radixdlt.sync.SyncConfig;
import com.radixdlt.utils.DurationParser;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt256;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/** High level BFT Simulation Test Runner */
public class SimulationTest {
  private static final String ENVIRONMENT_VAR_NAME =
      "TEST_DURATION"; // Same as used by regression test suite
  private static final Duration DEFAULT_TEST_DURATION = Duration.ofSeconds(30);

  public interface SimulationNetworkActor {
    void start(SimulationNodes.RunningNetwork network);

    void stop();
  }

  private final ImmutableList<ECKeyPair> nodes;
  private final SimulationNetwork simulationNetwork;
  private final Module testModule;
  private final Module baseNodeModule;
  private final Module overrideModule;
  private final Map<ECKeyPair, Module> byzantineNodeModules;

  private SimulationTest(
      ImmutableList<ECKeyPair> nodes,
      SimulationNetwork simulationNetwork,
      Module baseNodeModule,
      Module overrideModule,
      Map<ECKeyPair, Module> byzantineNodeModules,
      Module testModule) {
    this.nodes = nodes;
    this.simulationNetwork = simulationNetwork;
    this.baseNodeModule = baseNodeModule;
    this.overrideModule = overrideModule;
    this.byzantineNodeModules = byzantineNodeModules;
    this.testModule = testModule;
  }

  public static class Builder {
    private enum LedgerType {
      MOCKED_LEDGER(false, true, false, false, false, false, false),
      LEDGER(false, true, true, false, false, false, false),
      LEDGER_AND_SYNC(false, true, true, false, false, false, true),
      LEDGER_AND_LOCALMEMPOOL(false, true, true, true, false, false, false),
      LEDGER_AND_EPOCHS(false, true, true, false, false, true, false),
      LEDGER_AND_EPOCHS_AND_SYNC(false, true, true, false, false, true, true),
      LEDGER_AND_LOCALMEMPOOL_AND_EPOCHS_AND_RADIXENGINE(true, true, true, true, true, true, false),
      FULL_FUNCTION(true, true, true, true, true, true, true);

      private final boolean hasSharedMempool;
      private final boolean hasConsensus;
      private final boolean hasSync;

      // State manager
      private final boolean hasLedger;
      private final boolean hasMempool;
      private final boolean hasRadixEngine;

      private final boolean hasEpochs;

      LedgerType(
          boolean hasSharedMempool,
          boolean hasConsensus,
          boolean hasLedger,
          boolean hasMempool,
          boolean hasRadixEngine,
          boolean hasEpochs,
          boolean hasSync) {
        this.hasSharedMempool = hasSharedMempool;
        this.hasConsensus = hasConsensus;
        this.hasLedger = hasLedger;
        this.hasMempool = hasMempool;
        this.hasRadixEngine = hasRadixEngine;
        this.hasEpochs = hasEpochs;
        this.hasSync = hasSync;
      }
    }

    private ImmutableList<ECKeyPair> nodes = ImmutableList.of(ECKeyPair.generateNew());
    private long pacemakerTimeout = 12 * SimulationNetwork.DEFAULT_LATENCY;
    private LedgerType ledgerType = LedgerType.MOCKED_LEDGER;

    private Module initialNodesModule;
    private final ImmutableList.Builder<Module> testModules = ImmutableList.builder();
    private final ImmutableList.Builder<Module> modules = ImmutableList.builder();
    private Module networkModule;
    private Module overrideModule = null;
    private Function<ImmutableList<ECKeyPair>, ImmutableMap<ECKeyPair, Module>>
        byzantineModuleCreator = i -> ImmutableMap.of();
    private ImmutableMap<Integer, ImmutableList<Integer>> addressBookNodes;

    // TODO: Fix pacemaker so can Default 1 so can debug in IDE, possibly from properties at some
    // point
    // TODO: Specifically, simulation test with engine, epochs and mempool gets stuck on a single
    // validator
    private final int minValidators = 2;

    private Builder() {}

    public Builder addSingleByzantineModule(Module byzantineModule) {
      this.byzantineModuleCreator = nodes -> ImmutableMap.of(nodes.get(0), byzantineModule);
      return this;
    }

    public Builder addByzantineModuleToAll(Module byzantineModule) {
      this.byzantineModuleCreator =
          nodes ->
              nodes.stream()
                  .collect(
                      ImmutableMap.<ECKeyPair, ECKeyPair, Module>toImmutableMap(
                          n -> n, n -> byzantineModule));
      return this;
    }

    public Builder overrideWithIncorrectModule(Module module) {
      this.overrideModule = module;
      return this;
    }

    public Builder networkModules(Module... networkModules) {
      this.networkModule = Modules.combine(networkModules);
      return this;
    }

    public Builder addNetworkModule(Module networkModule) {
      this.networkModule = Modules.combine(this.networkModule, networkModule);
      return this;
    }

    public Builder pacemakerTimeout(long pacemakerTimeout) {
      this.pacemakerTimeout = pacemakerTimeout;
      return this;
    }

    /**
     * A mapping from a node index to a list of other nodes indices. If key is not present, then
     * address book for that node contains all other nodes.
     */
    public Builder addressBook(ImmutableMap<Integer, ImmutableList<Integer>> addressBookNodes) {
      this.addressBookNodes = addressBookNodes;
      return this;
    }

    public Builder numNodes(
        int numNodes, int numInitialValidators, Iterable<UInt256> initialStakes) {
      this.nodes =
          Stream.generate(ECKeyPair::generateNew)
              .limit(numNodes)
              .collect(ImmutableList.toImmutableList());

      final var stakesIterator = repeatLast(initialStakes);
      final var initialStakesMap =
          nodes.stream()
              .collect(
                  ImmutableMap.toImmutableMap(ECKeyPair::getPublicKey, k -> stakesIterator.next()));

      var initialVset =
          BFTValidatorSet.from(
              initialStakesMap.entrySet().stream()
                  .map(e -> BFTValidator.from(BFTNode.create(e.getKey()), e.getValue())));

      final var bftNodes =
          initialStakesMap.keySet().stream()
              .map(BFTNode::create)
              .collect(ImmutableList.toImmutableList());
      final var validators =
          initialStakesMap.entrySet().stream()
              .map(e -> BFTValidator.from(BFTNode.create(e.getKey()), e.getValue()))
              .collect(ImmutableList.toImmutableList());

      this.initialNodesModule =
          new AbstractModule() {
            @Override
            protected void configure() {
              bind(new TypeLiteral<ImmutableList<BFTNode>>() {}).toInstance(bftNodes);
              bind(new TypeLiteral<ImmutableList<BFTValidator>>() {}).toInstance(validators);
            }
          };

      this.modules.add(
          new AbstractModule() {
            @Override
            public void configure() {
              bind(new TypeLiteral<ImmutableList<BFTNode>>() {}).toInstance(bftNodes);
              bind(new TypeLiteral<ImmutableList<BFTValidator>>() {}).toInstance(validators);
            }
          });

      return this;
    }

    public Builder numNodes(int numNodes, int numInitialValidators) {
      return numNodes(numNodes, numInitialValidators, ImmutableList.of(UInt256.ONE));
    }

    public Builder numNodes(int numNodes) {
      return numNodes(numNodes, numNodes);
    }

    public Builder ledgerAndEpochs(
        View epochHighView, Function<Long, IntStream> epochToNodeIndexMapper) {
      this.ledgerType = LedgerType.LEDGER_AND_EPOCHS;
      this.modules.add(
          new AbstractModule() {
            @Override
            protected void configure() {
              bind(View.class).annotatedWith(EpochCeilingView.class).toInstance(epochHighView);
            }

            @Provides
            public Function<Long, BFTValidatorSet> epochToNodeMapper() {
              return epochToNodeIndexMapper.andThen(
                  indices ->
                      BFTValidatorSet.from(
                          indices
                              .mapToObj(nodes::get)
                              .map(node -> BFTNode.create(node.getPublicKey()))
                              .map(node -> BFTValidator.from(node, UInt256.ONE))
                              .toList()));
            }
          });

      return this;
    }

    public Builder ledger() {
      this.ledgerType = LedgerType.LEDGER;
      return this;
    }

    public Builder ledgerAndSync(SyncConfig syncConfig) {
      this.ledgerType = LedgerType.LEDGER_AND_SYNC;
      modules.add(
          new AbstractModule() {
            @Override
            protected void configure() {
              bind(SyncConfig.class).toInstance(syncConfig);
            }
          });
      return this;
    }

    public Builder fullFunctionNodes(SyncConfig syncConfig) {
      this.ledgerType = LedgerType.FULL_FUNCTION;
      modules.add(
          new AbstractModule() {
            @Override
            protected void configure() {
              bind(SyncConfig.class).toInstance(syncConfig);
              bind(new TypeLiteral<List<BFTNode>>() {}).toInstance(List.of());
            }
          });

      return this;
    }

    public Builder ledgerAndEpochsAndSync(
        View epochHighView,
        Function<Long, IntStream> epochToNodeIndexMapper,
        SyncConfig syncConfig) {
      this.ledgerType = LedgerType.LEDGER_AND_EPOCHS_AND_SYNC;
      modules.add(
          new AbstractModule() {
            @Override
            protected void configure() {
              bind(View.class).annotatedWith(EpochCeilingView.class).toInstance(epochHighView);
              bind(SyncConfig.class).toInstance(syncConfig);
            }

            @Provides
            public Function<Long, BFTValidatorSet> epochToNodeMapper() {
              return epochToNodeIndexMapper.andThen(
                  indices ->
                      BFTValidatorSet.from(
                          indices
                              .mapToObj(nodes::get)
                              .map(node -> BFTNode.create(node.getPublicKey()))
                              .map(node -> BFTValidator.from(node, UInt256.ONE))
                              .toList()));
            }
          });
      return this;
    }

    public Builder ledgerAndMempool() {
      this.ledgerType = LedgerType.LEDGER_AND_LOCALMEMPOOL;
      this.modules.add(MempoolConfig.asModule(10, 10));
      return this;
    }

    public Builder ledgerAndRadixEngineWithEpochHighView() {
      this.ledgerType = LedgerType.LEDGER_AND_LOCALMEMPOOL_AND_EPOCHS_AND_RADIXENGINE;
      this.modules.add(
          new AbstractModule() {
            @Override
            protected void configure() {
              bind(new TypeLiteral<List<BFTNode>>() {}).toInstance(List.of());
              install(MempoolConfig.asModule(100, 10));
            }

            @Provides
            CommittedReader committedReader() {
              return new CommittedReader() {
                @Override
                public VerifiedTxnsAndProof getNextCommittedTxns(DtoLedgerProof start) {
                  return null;
                }

                @Override
                public Optional<LedgerProof> getEpochProof(long epoch) {
                  return Optional.empty();
                }

                @Override
                public Optional<LedgerProof> getLastProof() {
                  return Optional.empty();
                }
              };
            }
          });

      return this;
    }

    public Builder addRadixEngineConfigModules(Module... modules) {
      this.modules.add(modules);
      this.testModules.add(modules);
      return this;
    }

    public Builder addNodeModule(Module module) {
      this.modules.add(module);
      return this;
    }

    public Builder addTestModules(Module... modules) {
      this.testModules.add(modules);
      return this;
    }

    public Builder addMempoolSubmissionsSteadyState(
        Class<? extends TxnGenerator> txnGeneratorClass) {
      NodeSelector nodeSelector =
          this.ledgerType.hasEpochs ? new EpochsNodeSelector() : new BFTValidatorSetNodeSelector();
      this.testModules.add(
          new AbstractModule() {
            @Override
            public void configure() {
              var multibinder = Multibinder.newSetBinder(binder(), SimulationNetworkActor.class);
              multibinder.addBinding().to(LocalMempoolPeriodicSubmitter.class);
              bind(TxnGenerator.class).to(txnGeneratorClass);
            }

            @Provides
            @Singleton
            LocalMempoolPeriodicSubmitter mempoolSubmittor(TxnGenerator txnGenerator) {
              return new LocalMempoolPeriodicSubmitter(txnGenerator, nodeSelector);
            }
          });

      return this;
    }

    public Builder addActor(Class<? extends SimulationNetworkActor> c) {
      this.testModules.add(
          new AbstractModule() {
            @Override
            public void configure() {
              Multibinder.newSetBinder(binder(), SimulationNetworkActor.class).addBinding().to(c);
            }
          });
      return this;
    }
    ;

    public SimulationTest build() {
      final NodeEvents nodeEvents = new NodeEvents();

      // Config
      modules.add(
          new AbstractModule() {
            @Override
            public void configure() {
              bind(SystemCounters.class).to(SystemCountersImpl.class).in(Scopes.SINGLETON);
              bind(Addressing.class).toInstance(Addressing.ofNetwork(Network.LOCALNET));
              bindConstant().annotatedWith(BFTSyncPatienceMillis.class).to(200);
              bindConstant().annotatedWith(PacemakerTimeout.class).to(pacemakerTimeout);
              bindConstant().annotatedWith(PacemakerRate.class).to(2.0);
              bindConstant()
                  .annotatedWith(PacemakerMaxExponent.class)
                  .to(0); // Use constant timeout for now
              bind(RateLimiter.class)
                  .annotatedWith(GetVerticesRequestRateLimit.class)
                  .toInstance(RateLimiter.create(50.0));
              bind(NodeEvents.class).toInstance(nodeEvents);
              bind(PeerControl.class).toInstance(new NoOpPeerControl());
            }
          });
      modules.add(new MockedSystemModule());
      modules.add(new MockedKeyModule());
      modules.add(new MockedCryptoModule());
      modules.add(new MockedPeersViewModule(this.addressBookNodes));

      // Functional
      modules.add(
          new FunctionalNodeModule(
              ledgerType.hasConsensus,
              ledgerType.hasLedger,
              ledgerType.hasMempool,
              ledgerType.hasSharedMempool,
              ledgerType.hasRadixEngine,
              ledgerType.hasEpochs,
              ledgerType.hasSync));

      // Persistence
      if (ledgerType.hasRadixEngine) {
        modules.add(new MockedRadixEngineStoreModule());
        modules.add(
            new MockedGenesisModule(
                nodes.stream().map(ECKeyPair::getPublicKey).collect(Collectors.toSet()),
                Amount.ofTokens(1000000),
                Amount.ofTokens(10000)));
        modules.add(new LedgerRecoveryModule());
        modules.add(new ConsensusRecoveryModule());

        // FIXME: A bit of a hack
        testModules.add(
            new AbstractModule() {
              public void configure() {
                install(new MockedCryptoModule());
                install(new RadixEngineModule());
                install(
                    new MockedGenesisModule(
                        nodes.stream().map(ECKeyPair::getPublicKey).collect(Collectors.toSet()),
                        Amount.ofTokens(1000000),
                        Amount.ofTokens(10000)));
                bind(LedgerAccumulator.class).to(SimpleLedgerAccumulatorAndVerifier.class);
                bind(new TypeLiteral<EngineStore<LedgerAndBFTProof>>() {})
                    .toInstance(new InMemoryEngineStore<>());
                bind(SystemCounters.class).toInstance(new SystemCountersImpl());
                bind(CommittedReader.class).toInstance(CommittedReader.mocked());
              }

              @Genesis
              @Provides
              Txn genesis(@Genesis VerifiedTxnsAndProof txnsAndProof) {
                return txnsAndProof.getTxns().get(0);
              }
            });
      } else {
        modules.add(new MockedRecoveryModule());
        var initialVset =
            BFTValidatorSet.from(
                nodes.stream()
                    .map(e -> BFTValidator.from(BFTNode.create(e.getPublicKey()), UInt256.ONE)));
        modules.add(
            new AbstractModule() {
              public void configure() {
                bind(BFTValidatorSet.class).toInstance(initialVset);
              }
            });
      }

      modules.add(new MockedPersistenceStoreModule());

      // Testing
      modules.add(new SimulationNodeEventsModule());
      testModules.add(
          new AbstractModule() {
            @Override
            protected void configure() {
              Multibinder.newSetBinder(binder(), SimulationNetworkActor.class);
              bind(Key.get(new TypeLiteral<List<ECKeyPair>>() {})).toInstance(nodes);
              bind(NodeEvents.class).toInstance(nodeEvents);
            }
          });

      // Nodes
      final SimulationNetwork simulationNetwork =
          Guice.createInjector(initialNodesModule, new SimulationNetworkModule(), networkModule)
              .getInstance(SimulationNetwork.class);

      // Runners
      modules.add(new RxEnvironmentModule());
      if (ledgerType.hasLedger && ledgerType.hasSync) {
        modules.add(new MockedCommittedReaderModule());
      }

      return new SimulationTest(
          nodes,
          simulationNetwork,
          Modules.combine(modules.build()),
          overrideModule,
          byzantineModuleCreator.apply(this.nodes),
          Modules.combine(testModules.build()));
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  private Observable<Pair<Monitor, Optional<TestInvariantError>>> runChecks(
      Set<SimulationNetworkActor> runners,
      Map<Monitor, TestInvariant> checkers,
      SimulationNodes.RunningNetwork runningNetwork,
      Duration duration) {
    var assertions =
        checkers.keySet().stream()
            .map(
                name -> {
                  TestInvariant check = checkers.get(name);
                  return Pair.of(
                      name,
                      check
                          .check(runningNetwork)
                          .map(e -> Pair.of(name, e))
                          .publish()
                          .autoConnect(2));
                })
            .toList();

    var firstErrorSignal =
        Observable.merge(assertions.stream().map(Pair::getSecond).toList())
            .firstOrError()
            .map(Pair::getFirst);

    var results =
        assertions.stream()
            .map(
                assertion ->
                    assertion
                        .getSecond()
                        .takeUntil(
                            firstErrorSignal.flatMapObservable(
                                name ->
                                    !assertion.getFirst().equals(name)
                                        ? Observable.just(name)
                                        : Observable.never()))
                        .takeUntil(
                            Observable.timer(duration.get(ChronoUnit.SECONDS), TimeUnit.SECONDS))
                        .map(e -> Optional.of(e.getSecond()))
                        .first(Optional.empty())
                        .map(result -> Pair.of(assertion.getFirst(), result)))
            .toList();

    return Single.merge(results)
        .toObservable()
        .doOnSubscribe(d -> runners.forEach(r -> r.start(runningNetwork)));
  }

  /**
   * Runs the test for time configured via environment variable. If environment variable is missing
   * then default duration is used. Returns either once the duration has passed or if a check has
   * failed. Returns a map from the check name to the result.
   *
   * @return map of check results
   */
  public RunningSimulationTest run() {
    return run(getConfiguredDuration(), ImmutableMap.of());
  }

  public RunningSimulationTest run(Duration duration) {
    return run(duration, ImmutableMap.of());
  }

  /**
   * Get test duration.
   *
   * @return configured test duration.
   */
  public static Duration getConfiguredDuration() {
    return Optional.ofNullable(System.getenv(ENVIRONMENT_VAR_NAME))
        .flatMap(DurationParser::parse)
        .orElse(DEFAULT_TEST_DURATION);
  }

  /**
   * Runs the test for a given time. Returns either once the duration has passed or if a check has
   * failed. Returns a map from the check name to the result.
   *
   * @param duration duration to run test for
   * @param disabledModuleRunners a list of disabled module runners by node index
   * @return test results
   */
  public RunningSimulationTest run(
      Duration duration, ImmutableMap<Integer, ImmutableSet<String>> disabledModuleRunners) {
    Injector testInjector = Guice.createInjector(testModule);
    var runners =
        testInjector.getInstance(Key.get(new TypeLiteral<Set<SimulationNetworkActor>>() {}));
    var checkers =
        testInjector.getInstance(Key.get(new TypeLiteral<Map<Monitor, TestInvariant>>() {}));

    SimulationNodes bftNetwork =
        new SimulationNodes(
            nodes, simulationNetwork, baseNodeModule, overrideModule, byzantineNodeModules);
    SimulationNodes.RunningNetwork runningNetwork = bftNetwork.start(disabledModuleRunners);

    final var resultObservable =
        runChecks(runners, checkers, runningNetwork, duration)
            .doFinally(
                () -> {
                  runners.forEach(SimulationNetworkActor::stop);
                  bftNetwork.stop();
                });

    return new RunningSimulationTest(resultObservable, runningNetwork);
  }

  private static <T> Iterator<T> repeatLast(Iterable<T> iterable) {
    final var iterator = iterable.iterator();
    if (!iterator.hasNext()) {
      throw new IllegalArgumentException("Can't repeat an empty iterable");
    }
    return new Iterator<>() {
      T lastValue = null;

      @Override
      public boolean hasNext() {
        return true;
      }

      @Override
      public T next() {
        if (iterator.hasNext()) {
          this.lastValue = iterator.next();
        }
        return this.lastValue;
      }
    };
  }

  public static final class RunningSimulationTest {

    private final Observable<Pair<Monitor, Optional<TestInvariantError>>> resultObservable;
    private final SimulationNodes.RunningNetwork network;

    private RunningSimulationTest(
        Observable<Pair<Monitor, Optional<TestInvariantError>>> resultObservable,
        SimulationNodes.RunningNetwork network) {
      this.resultObservable = resultObservable;
      this.network = network;
    }

    public SimulationNodes.RunningNetwork getNetwork() {
      return network;
    }

    public Map<Monitor, Optional<TestInvariantError>> awaitCompletion() {
      return this.resultObservable
          .blockingStream()
          .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));
    }
  }
}
