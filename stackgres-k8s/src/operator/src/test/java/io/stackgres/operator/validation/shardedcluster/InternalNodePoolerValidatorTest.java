/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.validation.shardedcluster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.stackgres.common.crd.sgcluster.StackGresClusterPods;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterSpec;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardingType;
import io.stackgres.operator.common.StackGresShardedClusterReview;
import io.stackgres.operator.common.fixture.AdmissionReviewFixtures;
import io.stackgres.operatorframework.admissionwebhook.validating.ValidationFailed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InternalNodePoolerValidatorTest {

  private InternalNodePoolerValidator validator;

  @BeforeEach
  void setUp() {
    validator = new InternalNodePoolerValidator();
  }

  @Test
  void givenFeatureDisabled_shouldNotFail() throws ValidationFailed {
    StackGresShardedClusterReview review =
        AdmissionReviewFixtures.shardedCluster().loadCreate().get();
    review.getRequest().getObject().getSpec().setEnableInternalNodePooler(null);

    validator.validate(review);
  }

  @Test
  void givenFeatureEnabledOnCitusWithPoolingEnabled_shouldNotFail() throws ValidationFailed {
    StackGresShardedClusterReview review =
        AdmissionReviewFixtures.shardedCluster().loadCreate().get();
    StackGresShardedClusterSpec spec = review.getRequest().getObject().getSpec();
    spec.setType(StackGresShardingType.CITUS.toString());
    spec.setEnableInternalNodePooler(true);
    ensurePoolingEnabled(spec);

    validator.validate(review);
  }

  @Test
  void givenFeatureEnabledOnNonCitusType_shouldFail() {
    StackGresShardedClusterReview review =
        AdmissionReviewFixtures.shardedCluster().loadCreate().get();
    StackGresShardedClusterSpec spec = review.getRequest().getObject().getSpec();
    spec.setType(StackGresShardingType.DDP.toString());
    spec.setEnableInternalNodePooler(true);
    ensurePoolingEnabled(spec);

    ValidationFailed exception = assertThrows(ValidationFailed.class,
        () -> validator.validate(review));

    assertEquals("enableInternalNodePooler can only be used when sharding type is citus",
        exception.getResult().getMessage());
  }

  @Test
  void givenFeatureEnabledWithPoolingDisabledOnCoordinator_shouldFail() {
    StackGresShardedClusterReview review =
        AdmissionReviewFixtures.shardedCluster().loadCreate().get();
    StackGresShardedClusterSpec spec = review.getRequest().getObject().getSpec();
    spec.setType(StackGresShardingType.CITUS.toString());
    spec.setEnableInternalNodePooler(true);
    ensurePoolingEnabled(spec);
    spec.getCoordinator().getPods().setDisableConnectionPooling(true);

    ValidationFailed exception = assertThrows(ValidationFailed.class,
        () -> validator.validate(review));

    assertEquals("enableInternalNodePooler requires connection pooling to be enabled"
        + " on both coordinator and shards",
        exception.getResult().getMessage());
  }

  @Test
  void givenFeatureEnabledWithPoolingDisabledOnShards_shouldFail() {
    StackGresShardedClusterReview review =
        AdmissionReviewFixtures.shardedCluster().loadCreate().get();
    StackGresShardedClusterSpec spec = review.getRequest().getObject().getSpec();
    spec.setType(StackGresShardingType.CITUS.toString());
    spec.setEnableInternalNodePooler(true);
    ensurePoolingEnabled(spec);
    spec.getShards().getPods().setDisableConnectionPooling(true);

    ValidationFailed exception = assertThrows(ValidationFailed.class,
        () -> validator.validate(review));

    assertEquals("enableInternalNodePooler requires connection pooling to be enabled"
        + " on both coordinator and shards",
        exception.getResult().getMessage());
  }

  private void ensurePoolingEnabled(StackGresShardedClusterSpec spec) {
    if (spec.getCoordinator().getPods() == null) {
      spec.getCoordinator().setPods(new StackGresClusterPods());
    }
    spec.getCoordinator().getPods().setDisableConnectionPooling(false);
    if (spec.getShards().getPods() == null) {
      spec.getShards().setPods(new StackGresClusterPods());
    }
    spec.getShards().getPods().setDisableConnectionPooling(false);
  }
}
