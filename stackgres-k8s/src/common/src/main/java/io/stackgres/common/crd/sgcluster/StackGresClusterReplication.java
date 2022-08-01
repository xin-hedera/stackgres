/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.common.crd.sgcluster;

import java.util.List;
import java.util.Objects;

import javax.validation.Valid;
import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.Min;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.stackgres.common.StackGresUtil;
import io.stackgres.common.validation.FieldReference;
import io.stackgres.common.validation.FieldReference.ReferencedField;
import io.stackgres.common.validation.ValidEnum;
import io.sundr.builder.annotations.Buildable;

@JsonDeserialize
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@RegisterForReflection
@Buildable(editableEnabled = false, validationEnabled = false, lazyCollectionInitEnabled = false)
public class StackGresClusterReplication {

  @JsonProperty("mode")
  @ValidEnum(enumClass = StackGresReplicationMode.class, allowNulls = false,
      message = "mode must be async, sync or strict-sync")
  private String mode;

  @JsonProperty("role")
  @ValidEnum(enumClass = StackGresMainReplicationRole.class, allowNulls = false,
      message = "role must be ha or ha-read")
  private String role;

  @JsonProperty("syncInstances")
  @Min(value = 1)
  private Integer syncInstances;

  @JsonProperty("groups")
  @Valid
  private List<StackGresClusterReplicationGroup> groups;

  @ReferencedField("syncInstances")
  interface SyncInstances extends FieldReference { }

  @JsonIgnore
  @AssertTrue(message = "syncInstances must be set when mode is sync or strict-sync",
      payload = { SyncInstances.class })
  public boolean isSyncInstancesSetForSyncMode() {
    return !isSynchronousMode()
        || syncInstances != null;
  }

  @JsonIgnore
  public boolean isSynchronousMode() {
    return Objects.equals(StackGresReplicationMode.SYNC.toString(), mode)
        || Objects.equals(StackGresReplicationMode.STRICT_SYNC.toString(), mode);
  }

  @JsonIgnore
  public boolean isStrictSynchronousMode() {
    return Objects.equals(StackGresReplicationMode.STRICT_SYNC.toString(), mode);
  }

  public String getMode() {
    return mode;
  }

  public void setMode(String mode) {
    this.mode = mode;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public Integer getSyncInstances() {
    return syncInstances;
  }

  public void setSyncInstances(Integer syncInstances) {
    this.syncInstances = syncInstances;
  }

  public List<StackGresClusterReplicationGroup> getGroups() {
    return groups;
  }

  public void setGroups(List<StackGresClusterReplicationGroup> groups) {
    this.groups = groups;
  }

  @Override
  public int hashCode() {
    return Objects.hash(groups, mode, role, syncInstances);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof StackGresClusterReplication)) {
      return false;
    }
    StackGresClusterReplication other = (StackGresClusterReplication) obj;
    return Objects.equals(groups, other.groups) && Objects.equals(mode, other.mode)
        && Objects.equals(role, other.role) && Objects.equals(syncInstances, other.syncInstances);
  }

  public String toString() {
    return StackGresUtil.toPrettyYaml(this);
  }
}
