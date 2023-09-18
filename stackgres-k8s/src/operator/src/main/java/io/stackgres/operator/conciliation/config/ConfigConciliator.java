/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.config;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.stackgres.common.crd.sgconfig.StackGresConfig;
import io.stackgres.operator.conciliation.AbstractConciliator;
import io.stackgres.operator.conciliation.AbstractDeployedResourcesScanner;
import io.stackgres.operator.conciliation.DeployedResourcesCache;
import io.stackgres.operator.conciliation.RequiredResourceGenerator;

@ApplicationScoped
public class ConfigConciliator extends AbstractConciliator<StackGresConfig> {

  @Inject
  public ConfigConciliator(
      RequiredResourceGenerator<StackGresConfig> requiredResourceGenerator,
      AbstractDeployedResourcesScanner<StackGresConfig> deployedResourcesScanner,
      DeployedResourcesCache deployedResourcesCache) {
    super(requiredResourceGenerator, deployedResourcesScanner, deployedResourcesCache);
  }

}
