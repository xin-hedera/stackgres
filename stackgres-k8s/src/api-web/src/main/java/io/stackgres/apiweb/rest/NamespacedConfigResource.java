/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.apiweb.rest;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.Path;

import io.quarkus.security.Authenticated;
import io.stackgres.apiweb.dto.config.ConfigDto;
import io.stackgres.common.crd.sgconfig.StackGresConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@Path("namespaces/{namespace:[a-z0-9]([-a-z0-9]*[a-z0-9])?}/sgconfigs")
@RequestScoped
@Authenticated
public class NamespacedConfigResource
    extends AbstractNamespacedRestService<ConfigDto, StackGresConfig> {

  @Operation(
      responses = {
          @ApiResponse(responseCode = "200", description = "OK",
              content = {@Content(
                  mediaType = "application/json",
                  schema = @Schema(implementation = ConfigDto.class))})
      })
  @Override
  public ConfigDto get(String namespace, String name) {
    return super.get(namespace, name);
  }

}
