/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.validation.shardedcluster;

import java.util.Optional;

import io.stackgres.common.ErrorType;
import io.stackgres.common.crd.sgcluster.StackGresClusterPods;
import io.stackgres.common.crd.sgcluster.StackGresClusterSpec;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedCluster;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterSpec;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardingType;
import io.stackgres.operator.common.StackGresShardedClusterReview;
import io.stackgres.operator.validation.ValidationType;
import io.stackgres.operatorframework.admissionwebhook.Operation;
import io.stackgres.operatorframework.admissionwebhook.validating.ValidationFailed;
import jakarta.inject.Singleton;

@Singleton
@ValidationType(ErrorType.CONSTRAINT_VIOLATION)
public class InternalNodePoolerValidator implements ShardedClusterValidator {

  @Override
  public void validate(StackGresShardedClusterReview review) throws ValidationFailed {
    Operation operation = review.getRequest().getOperation();
    if (operation != Operation.CREATE && operation != Operation.UPDATE) {
      return;
    }

    StackGresShardedCluster cluster = review.getRequest().getObject();
    if (!Boolean.TRUE.equals(cluster.getSpec().getEnableInternalNodePooler())) {
      return;
    }

    if (!StackGresShardingType.CITUS.toString().equals(cluster.getSpec().getType())) {
      fail("enableInternalNodePooler can only be used when sharding type is citus");
    }

    if (isConnectionPoolingDisabled(cluster.getSpec().getCoordinator())
        || isConnectionPoolingDisabled(cluster.getSpec().getShards())) {
      fail("enableInternalNodePooler requires connection pooling to be enabled"
          + " on both coordinator and shards");
    }
  }

  private boolean isConnectionPoolingDisabled(StackGresClusterSpec spec) {
    return Optional.ofNullable(spec)
        .map(StackGresClusterSpec::getPods)
        .map(StackGresClusterPods::getDisableConnectionPooling)
        .orElse(false);
  }
}
