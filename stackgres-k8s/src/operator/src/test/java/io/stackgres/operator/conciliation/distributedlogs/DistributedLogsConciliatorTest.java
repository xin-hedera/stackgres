/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.distributedlogs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.stackgres.common.StackGresContext;
import io.stackgres.common.crd.sgcluster.StackGresCluster;
import io.stackgres.common.crd.sgdistributedlogs.StackGresDistributedLogs;
import io.stackgres.testutil.JsonUtil;
import io.stackgres.operator.cluster.factory.KubernetessMockResourceGenerationUtil;
import io.stackgres.operator.conciliation.Conciliator;
import io.stackgres.operator.conciliation.ConciliatorTest;
import io.stackgres.operator.conciliation.DeployedResourcesScanner;
import io.stackgres.operator.conciliation.ReconciliationResult;
import io.stackgres.operator.conciliation.RequiredResourceGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DistributedLogsConciliatorTest extends ConciliatorTest<StackGresDistributedLogs> {

  private static final StackGresDistributedLogs distributedLogs = JsonUtil
      .readFromJson("distributedlogs/default.json", StackGresDistributedLogs.class);

  @Mock
  private RequiredResourceGenerator<StackGresDistributedLogs> requiredResourceGenerator;

  @Mock
  private DeployedResourcesScanner<StackGresDistributedLogs> deployedResourcesScanner;

  @Mock
  private DistributedLogsStatusManager statusManager;

  @BeforeEach
  void setUp() {
    when(statusManager.isPendingRestart(getConciliationResource())).thenReturn(false);
  }

  @Override
  protected Conciliator<StackGresDistributedLogs> buildConciliator(List<HasMetadata> required,
                                                                   List<HasMetadata> deployed) {

    when(requiredResourceGenerator.getRequiredResources(distributedLogs))
        .thenReturn(required);
    when(deployedResourcesScanner.getDeployedResources(distributedLogs))
        .thenReturn(deployed);

    final DistributedLogsConciliator clusterConciliator = new DistributedLogsConciliator(statusManager);
    clusterConciliator.setRequiredResourceGenerator(requiredResourceGenerator);
    clusterConciliator.setDeployedResourcesScanner(deployedResourcesScanner);
    clusterConciliator.setResourceComparator(resourceComparator);
    return clusterConciliator;
  }

  @Override
  protected StackGresDistributedLogs getConciliationResource() {
    return distributedLogs;
  }

  @Test
  void conciliation_shouldIgnoreDeletionsOnResourcesMarkedWithReconciliationPauseUntilRestartAnnotationIfTheClusterIsPendingToRestart() {

    final List<HasMetadata> requiredResources = KubernetessMockResourceGenerationUtil
        .buildResources("test", "test");

    final List<HasMetadata> deployedResources = new ArrayList<>(requiredResources);

    int indexToRemove = new Random().nextInt(requiredResources.size());
    deployedResources.get(indexToRemove).getMetadata().setAnnotations(Map.of(
        StackGresContext.RECONCILIATION_PAUSE_UNTIL_RESTART_KEY,
        Boolean.TRUE.toString()
    ));

    requiredResources.remove(indexToRemove);

    Conciliator<StackGresDistributedLogs> conciliator = buildConciliator(requiredResources,
        deployedResources);

    reset(statusManager);
    when(statusManager.isPendingRestart(distributedLogs))
        .thenReturn(true);

    ReconciliationResult result = conciliator.evalReconciliationState(getConciliationResource());
    assertEquals(0, result.getDeletions().size());

    assertTrue(result.isUpToDate());

  }

  @Test
  void conciliation_shouldNotIgnoreDeletionsOnResourcesMarkedWithReconciliationPauseUntilRestartAnnotationIfTheClusterIsNotPendingToRestart() {

    final List<HasMetadata> requiredResources = KubernetessMockResourceGenerationUtil
        .buildResources("test", "test");

    final List<HasMetadata> deployedResources = new ArrayList<>(requiredResources);

    int indexToRemove = new Random().nextInt(requiredResources.size());
    deployedResources.get(indexToRemove).getMetadata().setAnnotations(Map.of(
        StackGresContext.RECONCILIATION_PAUSE_UNTIL_RESTART_KEY,
        Boolean.TRUE.toString()
    ));

    requiredResources.remove(indexToRemove);

    Conciliator<StackGresDistributedLogs> conciliator = buildConciliator(requiredResources,
        deployedResources);

    when(statusManager.isPendingRestart(distributedLogs))
        .thenReturn(false);

    ReconciliationResult result = conciliator.evalReconciliationState(getConciliationResource());
    assertEquals(1, result.getDeletions().size());

    assertFalse(result.isUpToDate());

  }

  @Test
  void conciliation_shouldIgnoreChangesOnResourcesMarkedWithReconciliationPauseUntilRestartAnnotationIfTheClusterIsPendingToRestart() {

    final List<HasMetadata> requiredResources = KubernetessMockResourceGenerationUtil
        .buildResources("test", "test");
    final List<HasMetadata> deployedResources = deepCopy(requiredResources);

    deployedResources.stream().findAny()
        .orElseThrow().getMetadata().setAnnotations(Map.of(
        StackGresContext.RECONCILIATION_PAUSE_UNTIL_RESTART_KEY, Boolean.TRUE.toString()
    ));

    Conciliator<StackGresDistributedLogs> conciliator = buildConciliator(requiredResources, deployedResources);

    reset(statusManager);
    when(statusManager.isPendingRestart(distributedLogs))
        .thenReturn(true);

    ReconciliationResult result = conciliator.evalReconciliationState(getConciliationResource());

    assertEquals(0, result.getPatches().size());

    assertTrue(result.isUpToDate());

  }

  @Test
  void conciliation_shouldNotIgnoreChangesOnResourcesMarkedWithReconciliationPauseUntilRestartAnnotationIfTheClusterIsNotPendingToRestart() {

    final List<HasMetadata> requiredResources = KubernetessMockResourceGenerationUtil
        .buildResources("test", "test");
    final List<HasMetadata> deployedResources = deepCopy(requiredResources);

    deployedResources.stream().findAny()
        .orElseThrow().getMetadata().setAnnotations(Map.of(
        StackGresContext.RECONCILIATION_PAUSE_UNTIL_RESTART_KEY, Boolean.TRUE.toString()
    ));

    Conciliator<StackGresDistributedLogs> conciliator = buildConciliator(requiredResources, deployedResources);

    when(statusManager.isPendingRestart(distributedLogs))
        .thenReturn(false);

    ReconciliationResult result = conciliator.evalReconciliationState(distributedLogs);

    assertEquals(1, result.getPatches().size());

    assertFalse(result.isUpToDate());

  }
}