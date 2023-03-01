/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.cluster;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.stackgres.common.CdiUtil;
import io.stackgres.common.PatroniUtil;
import io.stackgres.common.crd.sgcluster.StackGresCluster;
import io.stackgres.operator.conciliation.ReconciliationScope;
import org.jooq.lambda.Seq;
import org.jooq.lambda.tuple.Tuple2;

@ReconciliationScope(value = StackGresCluster.class, kind = "Endpoints")
@ApplicationScoped
public class ClusterEndpointsReconciliationHandler
    extends ClusterDefaultReconciliationHandler {

  private final KubernetesClient client;
  private final ObjectMapper objectMapper;

  @Inject
  public ClusterEndpointsReconciliationHandler(KubernetesClient client,
      ObjectMapper objectMapper) {
    super(client);
    this.client = client;
    this.objectMapper = objectMapper;
  }

  public ClusterEndpointsReconciliationHandler() {
    super(null);
    CdiUtil.checkPublicNoArgsConstructorIsCalledToCreateProxy(getClass());
    this.client = null;
    this.objectMapper = null;
  }

  @Override
  public HasMetadata create(StackGresCluster context, HasMetadata resource) {
    if (isPatroniConfigEndpoint(context, resource)) {
      return createPatroniConfig(resource);
    }
    return super.create(context, resource);
  }

  @Override
  public HasMetadata patch(StackGresCluster context, HasMetadata resource,
      HasMetadata oldResource) {
    if (isPatroniConfigEndpoint(context, resource)) {
      return updatePatroniConfig(resource);
    }
    return super.patch(context, resource, oldResource);
  }

  @Override
  public HasMetadata replace(StackGresCluster context, HasMetadata resource) {
    if (isPatroniConfigEndpoint(context, resource)) {
      return updatePatroniConfig(resource);
    }
    return super.replace(context, resource);
  }

  private boolean isPatroniConfigEndpoint(StackGresCluster context, HasMetadata resource) {
    return resource.getMetadata().getName().equals(PatroniUtil.configName(context));
  }

  private HasMetadata createPatroniConfig(HasMetadata resource) {
    return client.resource(resource)
        .create();
  }

  private HasMetadata updatePatroniConfig(HasMetadata resource) {
    return Optional.ofNullable(client.resource(resource).get())
        .map(Endpoints.class::cast)
        .map(foundResource -> client.resource(new EndpointsBuilder(foundResource)
            .editMetadata()
            .withLabels(Seq.seq(resource.getMetadata().getLabels())
                .append(Optional.ofNullable(foundResource.getMetadata().getLabels())
                    .stream()
                    .flatMap(Seq::seq)
                    .filter(e -> !resource.getMetadata().getLabels().containsKey(e.v1)))
                .toMap(Tuple2::v1, Tuple2::v2))
            .withAnnotations(Seq.seq(resource.getMetadata().getAnnotations())
                .filter(e -> !Objects.equals(PatroniUtil.CONFIG_KEY, e.v1))
                .append(Optional.ofNullable(foundResource.getMetadata().getAnnotations())
                    .stream()
                    .flatMap(Seq::seq)
                    .filter(e -> !Objects.equals(PatroniUtil.CONFIG_KEY, e.v1))
                    .filter(e -> !resource.getMetadata().getAnnotations().containsKey(e.v1)))
                .append(Seq.seq(Map
                    .of(PatroniUtil.CONFIG_KEY, mergeConfig(foundResource, resource))))
                .toMap(Tuple2::v1, Tuple2::v2))
            .endMetadata()
            .build())
            .lockResourceVersion(foundResource.getMetadata().getResourceVersion())
            .replace())
        .orElseThrow(() -> new RuntimeException("Endpoints "
            + resource.getMetadata().getNamespace() + "." + resource.getMetadata().getName()
            + " was not found"));
  }

  private String mergeConfig(HasMetadata foundResource, HasMetadata resource) {
    try {
      JsonNode foundConfig = objectMapper.readTree(Optional
          .ofNullable(foundResource.getMetadata().getAnnotations())
          .map(map -> map.get(PatroniUtil.CONFIG_KEY))
          .orElse("{}"));
      JsonNode updatedConfig = objectMapper.readerForUpdating(foundConfig)
          .readTree(resource.getMetadata().getAnnotations().get(PatroniUtil.CONFIG_KEY));
      return updatedConfig.toString();
    } catch (JsonProcessingException ex) {
      throw new RuntimeException(ex);
    }
  }

  private JsonNode deepMerge(JsonNode required, JsonNode deployed) {
    if (required instanceof ObjectNode requiredObject
        && deployed instanceof ObjectNode deployedObject) {
      return objectDeepMerge(requiredObject, deployedObject);
    }

    return required;
  }

  private ObjectNode objectDeepMerge(ObjectNode required, ObjectNode deployed) {
    Seq.seq(deployed.fields()).toList().stream()
        .filter(e -> !required.has(e.getKey()))
        .forEach(e -> required
            .set(e.getKey(), deepMerge(required.get(e.getKey()), e.getValue())));
    return required;
  }

}
