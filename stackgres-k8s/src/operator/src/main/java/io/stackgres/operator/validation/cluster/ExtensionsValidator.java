/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.validation.cluster;

import java.util.List;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.stackgres.common.ExtensionTuple;
import io.stackgres.common.StackGresComponent;
import io.stackgres.common.StackGresUtil;
import io.stackgres.common.StackGresVersion;
import io.stackgres.common.crd.sgcluster.StackGresCluster;
import io.stackgres.common.crd.sgcluster.StackGresClusterExtension;
import io.stackgres.common.crd.sgcluster.StackGresClusterInstalledExtension;
import io.stackgres.common.crd.sgcluster.StackGresClusterPostgres;
import io.stackgres.common.crd.sgcluster.StackGresClusterSpec;
import io.stackgres.common.extension.ExtensionMetadataManager;
import io.stackgres.operator.common.StackGresClusterReview;
import io.stackgres.operator.validation.AbstractExtensionsValidator;
import io.stackgres.operatorframework.admissionwebhook.validating.ValidationFailed;

@Singleton
public class ExtensionsValidator
    extends AbstractExtensionsValidator<StackGresCluster, StackGresClusterReview>
    implements ClusterValidator {

  private final ExtensionMetadataManager extensionMetadataManager;

  @Inject
  public ExtensionsValidator(ExtensionMetadataManager extensionMetadataManager) {
    super();
    this.extensionMetadataManager = extensionMetadataManager;
  }

  @Override
  protected ExtensionMetadataManager getExtensionMetadataManager() {
    return extensionMetadataManager;
  }

  @Override
  protected List<ExtensionTuple> getDefaultExtensions(
      StackGresCluster resource) {
    String pgVersion = resource.getSpec().getPostgres().getVersion();
    StackGresComponent flavor = StackGresUtil.getPostgresFlavorComponent(resource);
    StackGresVersion stackGresVersion = StackGresVersion.getStackGresVersion(resource);
    return StackGresUtil.getDefaultClusterExtensions(pgVersion, flavor, stackGresVersion);
  }

  @Override
  protected List<StackGresClusterExtension> getExtensions(StackGresCluster resource) {
    return Optional.ofNullable(resource.getSpec())
        .map(StackGresClusterSpec::getPostgres)
        .map(StackGresClusterPostgres::getExtensions)
        .orElse(List.of());
  }

  protected Optional<List<StackGresClusterInstalledExtension>> getToInstallExtensions(
      StackGresCluster resource) {
    return Optional.ofNullable(resource.getSpec())
        .map(StackGresClusterSpec::getToInstallPostgresExtensions);
  }

  @Override
  protected StackGresCluster getCluster(StackGresCluster resource) {
    return resource;
  }

  @Override
  protected void failValidation(String reason, String message) throws ValidationFailed {
    fail(reason, message);
  }

}
