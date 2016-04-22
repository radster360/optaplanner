/*
 * Copyright 2010 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.optaplanner.core.config.localsearch;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import org.optaplanner.core.config.heuristic.policy.HeuristicConfigPolicy;
import org.optaplanner.core.config.heuristic.selector.common.SelectionCacheType;
import org.optaplanner.core.config.heuristic.selector.common.SelectionOrder;
import org.optaplanner.core.config.heuristic.selector.move.MoveSelectorConfig;
import org.optaplanner.core.config.heuristic.selector.move.composite.CartesianProductMoveSelectorConfig;
import org.optaplanner.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import org.optaplanner.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import org.optaplanner.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig;
import org.optaplanner.core.config.localsearch.decider.acceptor.AcceptorConfig;
import org.optaplanner.core.config.localsearch.decider.acceptor.AcceptorType;
import org.optaplanner.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import org.optaplanner.core.config.phase.PhaseConfig;
import org.optaplanner.core.config.solver.EnvironmentMode;
import org.optaplanner.core.config.util.ConfigUtils;
import org.optaplanner.core.impl.heuristic.selector.move.MoveSelector;
import org.optaplanner.core.impl.localsearch.DefaultLocalSearchPhase;
import org.optaplanner.core.impl.localsearch.LocalSearchPhase;
import org.optaplanner.core.impl.localsearch.decider.LocalSearchDecider;
import org.optaplanner.core.impl.localsearch.decider.acceptor.Acceptor;
import org.optaplanner.core.impl.localsearch.decider.forager.Forager;
import org.optaplanner.core.impl.solver.recaller.BestSolutionRecaller;
import org.optaplanner.core.impl.solver.termination.Termination;

import static org.apache.commons.lang3.ObjectUtils.*;

@XStreamAlias("localSearch")
public class LocalSearchPhaseConfig extends PhaseConfig<LocalSearchPhaseConfig> {

    // Warning: all fields are null (and not defaulted) because they can be inherited
    // and also because the input config file should match the output config file

    protected LocalSearchType localSearchType = null;

    // TODO This is a List due to XStream limitations. With JAXB it could be just a MoveSelectorConfig instead.
    @XStreamImplicit()
    private List<MoveSelectorConfig> moveSelectorConfigList = null;
    @XStreamAlias("acceptor")
    private AcceptorConfig acceptorConfig = null;
    @XStreamAlias("forager")
    private LocalSearchForagerConfig foragerConfig = null;

    public LocalSearchType getLocalSearchType() {
        return localSearchType;
    }

    public void setLocalSearchType(LocalSearchType localSearchType) {
        this.localSearchType = localSearchType;
    }

    public MoveSelectorConfig getMoveSelectorConfig() {
        return moveSelectorConfigList == null ? null : moveSelectorConfigList.get(0);
    }

    public void setMoveSelectorConfig(MoveSelectorConfig moveSelectorConfig) {
        this.moveSelectorConfigList = moveSelectorConfig == null ? null : Collections.singletonList(moveSelectorConfig);
    }

    public AcceptorConfig getAcceptorConfig() {
        return acceptorConfig;
    }

    public void setAcceptorConfig(AcceptorConfig acceptorConfig) {
        this.acceptorConfig = acceptorConfig;
    }

    public LocalSearchForagerConfig getForagerConfig() {
        return foragerConfig;
    }

    public void setForagerConfig(LocalSearchForagerConfig foragerConfig) {
        this.foragerConfig = foragerConfig;
    }

    // ************************************************************************
    // Builder methods
    // ************************************************************************

    @Override
    public LocalSearchPhase buildPhase(int phaseIndex, HeuristicConfigPolicy solverConfigPolicy,
            BestSolutionRecaller bestSolutionRecaller, Termination solverTermination) {
        HeuristicConfigPolicy phaseConfigPolicy = solverConfigPolicy.createPhaseConfigPolicy();
        DefaultLocalSearchPhase phase = new DefaultLocalSearchPhase();
        configurePhase(phase, phaseIndex, phaseConfigPolicy, bestSolutionRecaller, solverTermination);
        phase.setDecider(buildDecider(phaseConfigPolicy,
                phase.getTermination()));
        EnvironmentMode environmentMode = phaseConfigPolicy.getEnvironmentMode();
        if (environmentMode.isNonIntrusiveFullAsserted()) {
            phase.setAssertStepScoreFromScratch(true);
        }
        if (environmentMode.isIntrusiveFastAsserted()) {
            phase.setAssertExpectedStepScore(true);
            phase.setAssertShadowVariablesAreNotStaleAfterStep(true);
        }
        return phase;
    }

    private LocalSearchDecider buildDecider(HeuristicConfigPolicy configPolicy, Termination termination) {
        LocalSearchDecider decider = new LocalSearchDecider();
        decider.setTermination(termination);
        MoveSelector moveSelector = buildMoveSelector(configPolicy);
        decider.setMoveSelector(moveSelector);
        Acceptor acceptor = buildAcceptor(configPolicy);
        decider.setAcceptor(acceptor);
        Forager forager = buildForager(configPolicy);
        decider.setForager(forager);
        if (moveSelector.isNeverEnding() && !forager.supportsNeverEndingMoveSelector()) {
            throw new IllegalStateException("The moveSelector (" + moveSelector
                    + ") has neverEnding (" + moveSelector.isNeverEnding()
                    + "), but the forager (" + forager
                    + ") does not support it."
                    + " Configure the <forager> with <acceptedCountLimit>.");
        }
        EnvironmentMode environmentMode = configPolicy.getEnvironmentMode();
        if (environmentMode.isNonIntrusiveFullAsserted()) {
            decider.setAssertMoveScoreFromScratch(true);
        }
        if (environmentMode.isIntrusiveFastAsserted()) {
            decider.setAssertExpectedUndoMoveScore(true);
        }
        return decider;
    }

    protected Acceptor buildAcceptor(HeuristicConfigPolicy configPolicy) {
        AcceptorConfig acceptorConfig_;
        if (acceptorConfig != null) {
            if (localSearchType != null) {
                throw new IllegalArgumentException("The localSearchType (" + localSearchType
                        + ") must not be configured if the acceptorConfig (" + acceptorConfig
                        + ") is explicitly configured.");
            }
            acceptorConfig_ = acceptorConfig;
        } else {
            LocalSearchType localSearchType_ = defaultIfNull(localSearchType, LocalSearchType.LATE_ACCEPTANCE);
            acceptorConfig_ = new AcceptorConfig();
            switch (localSearchType_) {
                case HILL_CLIMBING:
                    acceptorConfig_.setAcceptorTypeList(Collections.singletonList(AcceptorType.HILL_CLIMBING));
                    break;
                case TABU_SEARCH:
                    acceptorConfig_.setAcceptorTypeList(Collections.singletonList(AcceptorType.ENTITY_TABU));
                    break;
                case SIMULATED_ANNEALING:
                    acceptorConfig_.setAcceptorTypeList(Collections.singletonList(AcceptorType.SIMULATED_ANNEALING));
                    break;
                case LATE_ACCEPTANCE:
                    acceptorConfig_.setAcceptorTypeList(Collections.singletonList(AcceptorType.LATE_ACCEPTANCE));
                    break;
                default:
                    throw new IllegalStateException("The localSearchType (" + localSearchType_
                            + ") is not implemented.");
            }
        }
        return acceptorConfig_.buildAcceptor(configPolicy);
    }

    protected Forager buildForager(HeuristicConfigPolicy configPolicy) {
        LocalSearchForagerConfig foragerConfig_;
        if (foragerConfig != null) {
            if (localSearchType != null) {
                throw new IllegalArgumentException("The localSearchType (" + localSearchType
                        + ") must not be configured if the foragerConfig (" + foragerConfig
                        + ") is explicitly configured.");
            }
            foragerConfig_ = foragerConfig;
        } else {
            LocalSearchType localSearchType_ = defaultIfNull(localSearchType, LocalSearchType.LATE_ACCEPTANCE);
            foragerConfig_ = new LocalSearchForagerConfig();
            switch (localSearchType_) {
                case HILL_CLIMBING:
                    foragerConfig_.setAcceptedCountLimit(1);
                    break;
                case TABU_SEARCH:
                    // Slow stepping algorithm
                    foragerConfig_.setAcceptedCountLimit(1000);
                    break;
                case SIMULATED_ANNEALING:
                case LATE_ACCEPTANCE:
                    // Fast stepping algorithm
                    foragerConfig_.setAcceptedCountLimit(1);
                    break;
                default:
                    throw new IllegalStateException("The localSearchType (" + localSearchType_
                            + ") is not implemented.");
            }
        }
        return foragerConfig_.buildForager(configPolicy);
    }

    protected MoveSelector buildMoveSelector(HeuristicConfigPolicy configPolicy) {
        MoveSelector moveSelector;
        SelectionCacheType defaultCacheType = SelectionCacheType.JUST_IN_TIME;
        SelectionOrder defaultSelectionOrder = SelectionOrder.RANDOM;
        if (ConfigUtils.isEmptyCollection(moveSelectorConfigList)) {
            // Default to changeMoveSelector and swapMoveSelector
            UnionMoveSelectorConfig unionMoveSelectorConfig = new UnionMoveSelectorConfig();
            unionMoveSelectorConfig.setMoveSelectorConfigList(Arrays.<MoveSelectorConfig>asList(
                    new ChangeMoveSelectorConfig(), new SwapMoveSelectorConfig()));
            moveSelector = unionMoveSelectorConfig.buildMoveSelector(configPolicy,
                    defaultCacheType, defaultSelectionOrder);
        } else if (moveSelectorConfigList.size() == 1) {
            moveSelector = moveSelectorConfigList.get(0).buildMoveSelector(
                    configPolicy, defaultCacheType, defaultSelectionOrder);
        } else {
            // TODO moveSelectorConfigList is only a List because of XStream limitations.
            throw new IllegalArgumentException("The moveSelectorConfigList (" + moveSelectorConfigList
                    + ") must be a singleton or empty. Use a single " + UnionMoveSelectorConfig.class
                    + " or " + CartesianProductMoveSelectorConfig.class
                    + " element to nest multiple MoveSelectors.");
        }
        return moveSelector;
    }

    @Override
    public void inherit(LocalSearchPhaseConfig inheritedConfig) {
        super.inherit(inheritedConfig);
        localSearchType = ConfigUtils.inheritOverwritableProperty(localSearchType,
                inheritedConfig.getLocalSearchType());
        setMoveSelectorConfig(ConfigUtils.inheritOverwritableProperty(
                getMoveSelectorConfig(), inheritedConfig.getMoveSelectorConfig()));
        acceptorConfig = ConfigUtils.inheritConfig(acceptorConfig, inheritedConfig.getAcceptorConfig());
        foragerConfig = ConfigUtils.inheritConfig(foragerConfig, inheritedConfig.getForagerConfig());
    }

}
