/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.app;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.MutatingWebhookBuilder;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.MutatingWebhookConfiguration;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.MutatingWebhookConfigurationBuilder;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.RuleWithOperationsBuilder;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.ValidatingWebhookBuilder;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.ValidatingWebhookConfiguration;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.ValidatingWebhookConfigurationBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceConversionBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.v1.ServiceReferenceBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.v1.WebhookClientConfigBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.v1.WebhookConversionBuilder;
import io.stackgres.common.CrdLoader;
import io.stackgres.common.OperatorProperty;
import io.stackgres.common.YamlMapperProvider;
import io.stackgres.common.crd.sgconfig.StackGresConfig;
import io.stackgres.common.resource.ResourceFinder;
import io.stackgres.common.resource.ResourceWriter;
import io.stackgres.operator.conversion.ConversionUtil;
import io.stackgres.operator.mutation.MutationUtil;
import io.stackgres.operator.validation.ValidationUtil;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class CrdWebhookConfigurator {

  private static final Logger LOGGER = LoggerFactory.getLogger(CrdWebhookConfigurator.class);

  String operatorName = OperatorProperty.OPERATOR_NAME.getString();

  String operatorNamespace = OperatorProperty.OPERATOR_NAMESPACE.getString();

  @ConfigProperty(name = "quarkus.http.ssl.certificate.files")
  String operatorCertPath;

  private final ResourceFinder<CustomResourceDefinition> crdFinder;
  private final ResourceWriter<CustomResourceDefinition> crdWriter;
  private final ResourceFinder<ValidatingWebhookConfiguration> validatingWebhookConfigurationFinder;
  private final ResourceWriter<ValidatingWebhookConfiguration> validatingWebhookConfigurationWriter;
  private final ResourceFinder<MutatingWebhookConfiguration> mutatingWebhookConfigurationFinder;
  private final ResourceWriter<MutatingWebhookConfiguration> mutatingWebhookConfigurationWriter;
  private final CrdLoader crdLoader;
  private final Supplier<String> operatorCertSupplier;

  @Inject
  @SuppressFBWarnings(value = "MC_OVERRIDABLE_METHOD_CALL_IN_CONSTRUCTOR",
        justification = "False positive")
  public CrdWebhookConfigurator(
      ResourceFinder<CustomResourceDefinition> crdFinder,
      ResourceWriter<CustomResourceDefinition> crdWriter,
      ResourceFinder<ValidatingWebhookConfiguration> validatingWebhookConfigurationFinder,
      ResourceWriter<ValidatingWebhookConfiguration> validatingWebhookConfigurationWriter,
      ResourceFinder<MutatingWebhookConfiguration> mutatingWebhookConfigurationFinder,
      ResourceWriter<MutatingWebhookConfiguration> mutatingWebhookConfigurationWriter,
      YamlMapperProvider yamlMapperProvider) {
    this.crdFinder = crdFinder;
    this.crdWriter = crdWriter;
    this.validatingWebhookConfigurationFinder = validatingWebhookConfigurationFinder;
    this.validatingWebhookConfigurationWriter = validatingWebhookConfigurationWriter;
    this.mutatingWebhookConfigurationFinder = mutatingWebhookConfigurationFinder;
    this.mutatingWebhookConfigurationWriter = mutatingWebhookConfigurationWriter;
    this.crdLoader = new CrdLoader(yamlMapperProvider.get());
    this.operatorCertSupplier = this::readOperatorCertFromPath;
  }

  CrdWebhookConfigurator(
      ResourceFinder<CustomResourceDefinition> crdFinder,
      ResourceWriter<CustomResourceDefinition> crdWriter,
      ResourceFinder<ValidatingWebhookConfiguration> validatingWebhookConfigurationFinder,
      ResourceWriter<ValidatingWebhookConfiguration> validatingWebhookConfigurationWriter,
      ResourceFinder<MutatingWebhookConfiguration> mutatingWebhookConfigurationFinder,
      ResourceWriter<MutatingWebhookConfiguration> mutatingWebhookConfigurationWriter,
      YamlMapperProvider yamlMapperProvider,
      Supplier<String> operatorCertPathSupplier) {
    this.crdFinder = crdFinder;
    this.crdWriter = crdWriter;
    this.validatingWebhookConfigurationFinder = validatingWebhookConfigurationFinder;
    this.validatingWebhookConfigurationWriter = validatingWebhookConfigurationWriter;
    this.mutatingWebhookConfigurationFinder = mutatingWebhookConfigurationFinder;
    this.mutatingWebhookConfigurationWriter = mutatingWebhookConfigurationWriter;
    this.crdLoader = new CrdLoader(yamlMapperProvider.get());
    this.operatorCertSupplier = operatorCertPathSupplier;
  }

  public void configureWebhooks() {
    String webhookCaCert = getWebhookCaCert()
        .orElseThrow(() -> new RuntimeException("Operator certificates secret not found"));

    var crds = crdLoader.scanCrds();

    crds.forEach(crd -> configureWebhook(crd.getMetadata().getName(), webhookCaCert));

    configureValidatingWebhooks(webhookCaCert, crds);

    configureMutatingWebhooks(webhookCaCert, crds);
  }

  protected void configureValidatingWebhooks(
      String webhookCaCert, List<CustomResourceDefinition> crds) {
    var validatingWebhookFound = validatingWebhookConfigurationFinder.findByName(operatorName);
    var validatingWebhook = validatingWebhookFound
        .orElseGet(() -> new ValidatingWebhookConfigurationBuilder()
            .withNewMetadata()
            .withName(operatorName)
            .endMetadata()
            .build());
    validatingWebhook.setWebhooks(List.of());
    crds.stream()
        .filter(crd -> !Objects.equals(
            crd.getSpec().getNames().getKind(),
            StackGresConfig.KIND))
        .forEach(crd -> configureValidatingWebhookConfiguration(
            crd, validatingWebhook, webhookCaCert));
    if (validatingWebhookFound.isEmpty()) {
      validatingWebhookConfigurationWriter.create(validatingWebhook);
    } else {
      validatingWebhookConfigurationWriter.update(validatingWebhook);
    }
  }

  protected void configureMutatingWebhooks(
      String webhookCaCert, List<CustomResourceDefinition> crds) {
    var mutatingWebhookFound = mutatingWebhookConfigurationFinder.findByName(operatorName);
    var mutatingWebhook = mutatingWebhookFound
        .orElseGet(() -> new MutatingWebhookConfigurationBuilder()
            .withNewMetadata()
            .withName(operatorName)
            .endMetadata()
            .build());
    mutatingWebhook.setWebhooks(List.of());
    crds.stream()
        .filter(crd -> !Objects.equals(
            crd.getSpec().getNames().getKind(),
            StackGresConfig.KIND))
        .forEach(crd -> configureMutatingWebhookConfiguration(
            crd, mutatingWebhook, webhookCaCert));
    if (mutatingWebhookFound.isEmpty()) {
      mutatingWebhookConfigurationWriter.create(mutatingWebhook);
    } else {
      mutatingWebhookConfigurationWriter.update(mutatingWebhook);
    }
  }

  protected void configureWebhook(String name, String webhookCaCert) {
    CustomResourceDefinition customResourceDefinition = crdFinder.findByName(name)
        .orElseThrow(() -> new RuntimeException("Custom Resource Definition "
            + name + " not found"));
    customResourceDefinition.getSpec().setPreserveUnknownFields(false);

    String conversionPath = ConversionUtil.CONVERSION_PATH + "/"
        + customResourceDefinition.getSpec().getNames().getSingular();
    customResourceDefinition.getSpec().setConversion(new CustomResourceConversionBuilder()
        .withStrategy("Webhook")
        .withWebhook(new WebhookConversionBuilder()
            .withClientConfig(new WebhookClientConfigBuilder()
                .withCaBundle(webhookCaCert)
                .withService(new ServiceReferenceBuilder()
                    .withNamespace(operatorNamespace)
                    .withName(operatorName)
                    .withPath(conversionPath)
                    .build())
                .build())
            .withConversionReviewVersions("v1")
            .build())
        .build());
    crdWriter.update(customResourceDefinition);
  }

  protected void configureValidatingWebhookConfiguration(
      CustomResourceDefinition customResourceDefinition,
      ValidatingWebhookConfiguration validatingWebhook,
      String webhookCaCert) {
    validatingWebhook.setWebhooks(Stream.concat(
        validatingWebhook.getWebhooks().stream(),
        Stream.of(new ValidatingWebhookBuilder()
            .withName(customResourceDefinition.getSpec().getNames().getSingular()
                + ".validating-webhook." + customResourceDefinition.getSpec().getGroup())
            .withSideEffects("None")
            .withRules(List.of(new RuleWithOperationsBuilder()
                .withOperations("CREATE", "UPDATE", "DELETE")
                .withApiGroups(customResourceDefinition.getSpec().getGroup())
                .withApiVersions("*")
                .withResources(customResourceDefinition.getSpec().getNames().getPlural())
                .build()))
            .withFailurePolicy("Fail")
            .withNewClientConfig()
            .withNewService()
            .withNamespace(operatorNamespace)
            .withName(operatorName)
            .withPath(ValidationUtil.VALIDATION_PATH
                + "/" + customResourceDefinition.getSpec().getNames().getSingular())
            .endService()
            .withCaBundle(webhookCaCert)
            .endClientConfig()
            .withAdmissionReviewVersions("v1")
            .build()))
        .toList());
  }

  protected void configureMutatingWebhookConfiguration(
      CustomResourceDefinition customResourceDefinition,
      MutatingWebhookConfiguration mutatingWebhook,
      String webhookCaCert) {
    mutatingWebhook.setWebhooks(Stream.concat(
        mutatingWebhook.getWebhooks().stream(),
        Stream.of(new MutatingWebhookBuilder()
            .withName(customResourceDefinition.getSpec().getNames().getSingular()
                + ".mutating-webhook." + customResourceDefinition.getSpec().getGroup())
            .withSideEffects("None")
            .withRules(List.of(new RuleWithOperationsBuilder()
                .withOperations("CREATE", "UPDATE")
                .withApiGroups(customResourceDefinition.getSpec().getGroup())
                .withApiVersions("*")
                .withResources(customResourceDefinition.getSpec().getNames().getPlural())
                .build()))
            .withFailurePolicy("Fail")
            .withNewClientConfig()
            .withNewService()
            .withNamespace(operatorNamespace)
            .withName(operatorName)
            .withPath(MutationUtil.MUTATION_PATH
                + "/" + customResourceDefinition.getSpec().getNames().getSingular())
            .endService()
            .withCaBundle(webhookCaCert)
            .endClientConfig()
            .withAdmissionReviewVersions("v1")
            .build()))
        .toList());
  }

  protected Optional<String> getWebhookCaCert() {
    return Optional.ofNullable(operatorCertSupplier.get())
        .map(cert -> cert.getBytes(StandardCharsets.UTF_8))
        .map(Base64.getEncoder()::encodeToString);
  }

  protected String readOperatorCertFromPath() {
    try {
      return Files.readString(Paths.get(this.operatorCertPath));
    } catch (Exception ex) {
      LOGGER.warn("Can not read operator certificate {}", operatorCertPath, ex);
      return null;
    }
  }
}
