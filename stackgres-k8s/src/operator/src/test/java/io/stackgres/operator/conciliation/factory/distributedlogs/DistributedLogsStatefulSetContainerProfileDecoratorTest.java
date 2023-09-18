/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.factory.distributedlogs;

import static org.mockito.Mockito.lenient;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.stackgres.common.StackGresContainer;
import io.stackgres.common.StackGresContext;
import io.stackgres.common.StackGresGroupKind;
import io.stackgres.common.StackGresProperty;
import io.stackgres.common.StringUtil;
import io.stackgres.common.crd.sgdistributedlogs.StackGresDistributedLogs;
import io.stackgres.common.crd.sgdistributedlogs.StackGresDistributedLogsNonProduction;
import io.stackgres.common.crd.sgdistributedlogs.StackGresDistributedLogsResources;
import io.stackgres.common.crd.sgprofile.StackGresProfile;
import io.stackgres.common.crd.sgprofile.StackGresProfileContainer;
import io.stackgres.common.fixture.Fixtures;
import io.stackgres.operator.conciliation.distributedlogs.StackGresDistributedLogsContext;
import io.stackgres.operator.conciliation.factory.AbstractProfileDecoratorTestCase;
import io.stackgres.operator.conciliation.factory.cluster.KubernetessMockResourceGenerationUtil;
import org.jooq.lambda.Seq;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DistributedLogsStatefulSetContainerProfileDecoratorTest
    extends AbstractProfileDecoratorTestCase {

  private static final StackGresGroupKind KIND = StackGresGroupKind.CLUSTER;

  private final DistributedLogsStatefulSetContainerProfileDecorator profileDecorator =
      new DistributedLogsStatefulSetContainerProfileDecorator();

  @Mock
  private StackGresDistributedLogsContext context;

  private StackGresDistributedLogs distributedLogs;

  private StackGresProfile profile;

  private StatefulSet statefulSet;

  private List<HasMetadata> resources;

  @BeforeEach
  void setUp() {
    distributedLogs = Fixtures.distributedLogs().loadDefault().get();
    profile = Fixtures.instanceProfile().loadSizeXs().get();

    final ObjectMeta metadata = distributedLogs.getMetadata();
    metadata.getAnnotations().put(StackGresContext.VERSION_KEY,
        StackGresProperty.OPERATOR_VERSION.getString());
    resources = KubernetessMockResourceGenerationUtil
        .buildResources(metadata.getName(), metadata.getNamespace());
    statefulSet = resources.stream()
        .filter(StatefulSet.class::isInstance)
        .map(StatefulSet.class::cast)
        .findFirst()
        .orElseThrow();
    profile.getSpec().setContainers(new HashMap<>());
    profile.getSpec().setInitContainers(new HashMap<>());
    Seq.seq(statefulSet.getSpec().getTemplate().getSpec().getContainers())
        .forEach(container -> {
          StackGresProfileContainer containerProfile = new StackGresProfileContainer();
          containerProfile.setCpu(new Random().nextInt(32000) + "m");
          containerProfile.setMemory(new Random().nextInt(32) + "Gi");
          profile.getSpec().getContainers().put(
              KIND.getContainerPrefix() + container.getName(), containerProfile);
        });
    Seq.seq(statefulSet.getSpec().getTemplate().getSpec().getInitContainers())
        .forEach(container -> {
          StackGresProfileContainer containerProfile = new StackGresProfileContainer();
          containerProfile.setCpu(new Random().nextInt(32000) + "m");
          containerProfile.setMemory(new Random().nextInt(32) + "Gi");
          profile.getSpec().getInitContainers().put(
              KIND.getContainerPrefix() + container.getName(), containerProfile);
        });
    StackGresProfileContainer containerProfile = new StackGresProfileContainer();
    containerProfile.setCpu(new Random().nextInt(32000) + "m");
    containerProfile.setMemory(new Random().nextInt(32) + "Gi");
    profile.getSpec().getContainers().put(
        KIND.getContainerPrefix() + StringUtil.generateRandom(), containerProfile);
    profile.getSpec().getInitContainers().put(
        KIND.getContainerPrefix() + StringUtil.generateRandom(), containerProfile);

    lenient().when(context.getSource()).thenReturn(distributedLogs);
    lenient().when(context.getProfile()).thenReturn(profile);
  }

  @Override
  protected boolean filterContainers(Container container) {
    return !Objects.equals(
        container.getName(), StackGresContainer.PATRONI.getNameWithPrefix());
  }

  @Override
  protected StackGresProfile getProfile() {
    return profile;
  }

  @Override
  protected PodSpec getPodSpec() {
    return statefulSet.getSpec().getTemplate().getSpec();
  }

  @Override
  protected StackGresGroupKind getKind() {
    return KIND;
  }

  @Override
  protected void decorate() {
    resources.forEach(resource -> profileDecorator.decorate(context, resource));
  }

  @Override
  protected void disableResourceRequirements() {
    distributedLogs.getSpec().setNonProductionOptions(new StackGresDistributedLogsNonProduction());
    distributedLogs.getSpec().getNonProductionOptions().setDisableClusterResourceRequirements(true);
  }

  @Override
  protected void enableRequests() {
    distributedLogs.getSpec().setNonProductionOptions(new StackGresDistributedLogsNonProduction());
    distributedLogs.getSpec().getNonProductionOptions().setEnableSetClusterCpuRequests(true);
    distributedLogs.getSpec().getNonProductionOptions().setEnableSetClusterMemoryRequests(true);
  }

  @Override
  protected void enableLimits() {
    distributedLogs.getSpec().setResources(new StackGresDistributedLogsResources());
    distributedLogs.getSpec().getResources().setEnableClusterLimitsRequirements(true);
  }

}
