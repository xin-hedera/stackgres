#!/bin/sh

set -xe

for CRD in bundle/manifests/stackgres.io_*
do
  cp "config/crd/bases/$(yq -r .spec.names.kind "$CRD").yaml" "$CRD"
  CRD_NAME="$(yq -r '.metadata.name' "$CRD")"
  CRD_SINGULAR="$(yq -r .spec.names.singular "$CRD")"
  if [ "$CRD_SINGULAR" = SGConfig ]
  then
    yq -y '.spec.webhookdefinitions = (.spec.webhookdefinitions 
      | map(select((.type == "ConversionWebhook" and .conversionCRDs[0] == "'"$CRD_NAME"'") | not))
      )' bundle/manifests/stackgres.clusterserviceversion.yaml \
      > bundle/manifests/stackgres.clusterserviceversion.yaml.tmp
  else
    yq -y '.spec.webhookdefinitions = (.spec.webhookdefinitions 
      | map(
        if .type == "ConversionWebhook" and .conversionCRDs[0] == "'"$CRD_NAME"'"
          then .webhookPath = "/stackgres/conversion/'"$CRD_SINGULAR"'" else . end
        )
      )' bundle/manifests/stackgres.clusterserviceversion.yaml \
      > bundle/manifests/stackgres.clusterserviceversion.yaml.tmp
  fi
  mv bundle/manifests/stackgres.clusterserviceversion.yaml.tmp \
    bundle/manifests/stackgres.clusterserviceversion.yaml
done
yq -s -y '.[0] as $config | .[1] as $bundle | $bundle | .spec.relatedImages = $config.spec.relatedImages' \
  config/manifests/bases/stackgres.clusterserviceversion.yaml \
  bundle/manifests/stackgres.clusterserviceversion.yaml \
  > bundle/manifests/stackgres.clusterserviceversion.yaml.tmp
mv bundle/manifests/stackgres.clusterserviceversion.yaml.tmp \
  bundle/manifests/stackgres.clusterserviceversion.yaml
