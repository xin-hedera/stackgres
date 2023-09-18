/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.distributedlogs;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import io.stackgres.operator.conciliation.AbstractResourceDiscoverer;
import io.stackgres.operator.conciliation.ResourceGenerator;
import io.stackgres.operator.conciliation.factory.DecoratorDiscoverer;

@ApplicationScoped
public class DistributedLogsResourceGenerationDiscoverer
    extends AbstractResourceDiscoverer<StackGresDistributedLogsContext> {

  @Inject
  public DistributedLogsResourceGenerationDiscoverer(
      @Any Instance<ResourceGenerator<StackGresDistributedLogsContext>> instance,
      DecoratorDiscoverer<StackGresDistributedLogsContext> decoratorDiscoverer) {
    super(instance, decoratorDiscoverer);
  }

}
