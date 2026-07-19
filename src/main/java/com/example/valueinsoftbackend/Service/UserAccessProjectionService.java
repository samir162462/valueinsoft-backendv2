package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.Model.Configuration.AccessMap.AccessMapEdge;
import com.example.valueinsoftbackend.Model.Configuration.AccessMap.AccessMapNode;
import com.example.valueinsoftbackend.Model.Configuration.AccessMap.AccessMapRoleRef;
import com.example.valueinsoftbackend.Model.Configuration.AccessMap.AccessMapSummary;
import com.example.valueinsoftbackend.Model.Configuration.AccessMap.AccessMapUserSummary;
import com.example.valueinsoftbackend.Model.Configuration.AccessMap.UserAccessMapResponse;
import com.example.valueinsoftbackend.Model.Configuration.ConfigurationAdminUserSummary;
import com.example.valueinsoftbackend.Model.Configuration.EffectiveModuleConfig;
import com.example.valueinsoftbackend.Model.Configuration.PlatformCapabilityConfig;
import com.example.valueinsoftbackend.Model.Configuration.PlatformModuleConfig;
import com.example.valueinsoftbackend.Model.Configuration.RoleDefinitionConfig;
import com.example.valueinsoftbackend.Model.Configuration.RoleGrantConfig;
import com.example.valueinsoftbackend.Model.Configuration.TenantRoleAssignmentConfig;
import com.example.valueinsoftbackend.Model.Configuration.TenantUserGrantOverrideConfig;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Explainable, non-destructive projection of one user's access across the full
 * capability catalog.
 *
 * <p>This service NEVER replaces the trusted enforcement path
 * ({@link EffectiveConfigurationService} + AuthorizationService). It reuses the exact
 * same scope semantics via {@link CapabilityScopeRules} but collects every contributing
 * role grant and override instead of last-write-wins map replacement, and records
 * denies instead of deleting entries, so the graph can explain WHY access exists
 * or does not exist.</p>
 */
@Service
public class UserAccessProjectionService {

    /**
     * Immutable input bundle for one projection run.
     */
    public static class ProjectionInput {
        public final ConfigurationAdminUserSummary targetUser;
        public final int tenantId;
        public final Integer activeBranchId;
        public final boolean canMutate;
        public final List<PlatformCapabilityConfig> allCapabilities;
        public final List<PlatformModuleConfig> platformModules;
        public final List<EffectiveModuleConfig> effectiveModules;
        public final List<TenantRoleAssignmentConfig> roleAssignments;
        public final List<RoleGrantConfig> roleGrants;
        public final List<TenantUserGrantOverrideConfig> userOverrides;
        public final List<RoleDefinitionConfig> roleDefinitions;

        public ProjectionInput(ConfigurationAdminUserSummary targetUser,
                               int tenantId,
                               Integer activeBranchId,
                               boolean canMutate,
                               List<PlatformCapabilityConfig> allCapabilities,
                               List<PlatformModuleConfig> platformModules,
                               List<EffectiveModuleConfig> effectiveModules,
                               List<TenantRoleAssignmentConfig> roleAssignments,
                               List<RoleGrantConfig> roleGrants,
                               List<TenantUserGrantOverrideConfig> userOverrides,
                               List<RoleDefinitionConfig> roleDefinitions) {
            this.targetUser = targetUser;
            this.tenantId = tenantId;
            this.activeBranchId = activeBranchId;
            this.canMutate = canMutate;
            this.allCapabilities = allCapabilities;
            this.platformModules = platformModules;
            this.effectiveModules = effectiveModules;
            this.roleAssignments = roleAssignments;
            this.roleGrants = roleGrants;
            this.userOverrides = userOverrides;
            this.roleDefinitions = roleDefinitions;
        }
    }

    public UserAccessMapResponse buildAccessMap(ProjectionInput input) {
        Map<String, EffectiveModuleConfig> effectiveModulesById = new LinkedHashMap<>();
        for (EffectiveModuleConfig module : input.effectiveModules) {
            effectiveModulesById.put(module.getModuleId(), module);
        }
        Map<String, PlatformModuleConfig> platformModulesById = new LinkedHashMap<>();
        for (PlatformModuleConfig module : input.platformModules) {
            platformModulesById.put(module.getModuleId(), module);
        }
        Map<String, RoleDefinitionConfig> roleDefinitionsById = new LinkedHashMap<>();
        for (RoleDefinitionConfig definition : input.roleDefinitions) {
            roleDefinitionsById.put(definition.getRoleId(), definition);
        }
        Map<String, List<RoleGrantConfig>> roleGrantsByRoleId = new LinkedHashMap<>();
        for (RoleGrantConfig roleGrant : input.roleGrants) {
            roleGrantsByRoleId.computeIfAbsent(roleGrant.getRoleId(), key -> new ArrayList<>()).add(roleGrant);
        }
        Map<String, List<TenantUserGrantOverrideConfig>> overridesByCapability = new LinkedHashMap<>();
        for (TenantUserGrantOverrideConfig override : input.userOverrides) {
            overridesByCapability.computeIfAbsent(override.getCapabilityKey(), key -> new ArrayList<>()).add(override);
        }

        boolean ownerBypass = isOwner(input.targetUser);

        ArrayList<AccessMapNode> capabilityNodes = new ArrayList<>();
        for (PlatformCapabilityConfig capability : input.allCapabilities) {
            capabilityNodes.add(projectCapability(
                    capability,
                    effectiveModulesById.get(capability.getModuleId()),
                    input,
                    roleGrantsByRoleId,
                    overridesByCapability.getOrDefault(capability.getCapabilityKey(), List.of()),
                    roleDefinitionsById,
                    ownerBypass
            ));
        }

        ArrayList<AccessMapNode> nodes = new ArrayList<>();
        ArrayList<AccessMapEdge> edges = new ArrayList<>();

        AccessMapNode userNode = new AccessMapNode();
        String userNodeId = "user:" + input.targetUser.getUserId();
        userNode.setId(userNodeId);
        userNode.setType("USER");
        userNode.setLabel(displayName(input.targetUser));
        nodes.add(userNode);

        // Group capabilities module -> resource preserving catalog order.
        Map<String, Map<String, List<AccessMapNode>>> byModuleAndResource = new LinkedHashMap<>();
        for (AccessMapNode capabilityNode : capabilityNodes) {
            byModuleAndResource
                    .computeIfAbsent(capabilityNode.getModuleId(), key -> new LinkedHashMap<>())
                    .computeIfAbsent(capabilityNode.getResource(), key -> new ArrayList<>())
                    .add(capabilityNode);
        }

        for (Map.Entry<String, Map<String, List<AccessMapNode>>> moduleEntry : byModuleAndResource.entrySet()) {
            String moduleId = moduleEntry.getKey();
            EffectiveModuleConfig effectiveModule = effectiveModulesById.get(moduleId);
            PlatformModuleConfig platformModule = platformModulesById.get(moduleId);

            AccessMapNode moduleNode = new AccessMapNode();
            String moduleNodeId = "module:" + moduleId;
            moduleNode.setId(moduleNodeId);
            moduleNode.setParentId(userNodeId);
            moduleNode.setType("MODULE");
            moduleNode.setModuleId(moduleId);
            moduleNode.setLabel(effectiveModule != null ? effectiveModule.getDisplayName() : humanize(moduleId));
            moduleNode.setCategory(effectiveModule != null ? effectiveModule.getCategory()
                    : platformModule != null ? platformModule.getCategory() : null);
            moduleNode.setModuleAvailable(effectiveModule != null && effectiveModule.isEnabled());
            moduleNode.setAvailabilitySource(effectiveModule != null ? effectiveModule.getSource() : null);
            moduleNode.setModuleMode(effectiveModule != null ? effectiveModule.getMode() : null);
            moduleNode.setModuleStatus(platformModule != null ? platformModule.getStatus() : null);

            ArrayList<AccessMapNode> moduleCapabilities = new ArrayList<>();
            nodes.add(moduleNode);
            edges.add(hierarchyEdge(userNodeId, moduleNodeId));

            for (Map.Entry<String, List<AccessMapNode>> resourceEntry : moduleEntry.getValue().entrySet()) {
                String resource = resourceEntry.getKey();
                List<AccessMapNode> resourceCapabilities = resourceEntry.getValue();

                AccessMapNode resourceNode = new AccessMapNode();
                String resourceNodeId = "resource:" + moduleId + ":" + resource;
                resourceNode.setId(resourceNodeId);
                resourceNode.setParentId(moduleNodeId);
                resourceNode.setType("RESOURCE");
                resourceNode.setModuleId(moduleId);
                resourceNode.setResource(resource);
                resourceNode.setLabel(humanize(resource));
                applyAggregates(resourceNode, resourceCapabilities);

                nodes.add(resourceNode);
                edges.add(hierarchyEdge(moduleNodeId, resourceNodeId));

                for (AccessMapNode capabilityNode : resourceCapabilities) {
                    capabilityNode.setParentId(resourceNodeId);
                    nodes.add(capabilityNode);
                    edges.add(hierarchyEdge(resourceNodeId, capabilityNode.getId()));
                }
                moduleCapabilities.addAll(resourceCapabilities);
            }

            applyAggregates(moduleNode, moduleCapabilities);
            if (Boolean.FALSE.equals(moduleNode.getModuleAvailable())) {
                moduleNode.setAggregateState(AccessMapNode.AGGREGATE_LOCKED);
            }
        }

        AccessMapSummary summary = buildSummary(capabilityNodes);

        AccessMapUserSummary userSummary = new AccessMapUserSummary(
                input.targetUser.getUserId(),
                input.targetUser.getUserName(),
                displayName(input.targetUser),
                input.targetUser.getEmail(),
                input.targetUser.getBranchId() > 0 ? input.targetUser.getBranchId() : null,
                input.targetUser.getBranchName(),
                input.targetUser.getLegacyRole(),
                ownerBypass,
                buildAssignedRoles(input.roleAssignments, roleDefinitionsById)
        );

        return new UserAccessMapResponse(
                userSummary,
                input.tenantId,
                input.activeBranchId,
                input.canMutate,
                summary,
                nodes,
                edges
        );
    }

    private AccessMapNode projectCapability(PlatformCapabilityConfig capability,
                                            EffectiveModuleConfig effectiveModule,
                                            ProjectionInput input,
                                            Map<String, List<RoleGrantConfig>> roleGrantsByRoleId,
                                            List<TenantUserGrantOverrideConfig> capabilityOverrides,
                                            Map<String, RoleDefinitionConfig> roleDefinitionsById,
                                            boolean ownerBypass) {
        Integer activeBranchId = input.activeBranchId;
        boolean moduleAvailable = effectiveModule != null && effectiveModule.isEnabled();
        boolean deprecated = "deprecated".equalsIgnoreCase(capability.getStatus());

        // ---- Collect role contributions (non-destructive equivalent of the resolver loop). ----
        ArrayList<RoleHit> effectiveHits = new ArrayList<>();
        ArrayList<AccessMapRoleRef> inapplicableRoles = new ArrayList<>();
        for (TenantRoleAssignmentConfig assignment : input.roleAssignments) {
            List<RoleGrantConfig> grants = roleGrantsByRoleId.get(assignment.getRoleId());
            if (grants == null) {
                continue;
            }
            for (RoleGrantConfig grant : grants) {
                if (!capability.getCapabilityKey().equals(grant.getCapabilityKey())) {
                    continue;
                }
                if (!"allow".equalsIgnoreCase(grant.getGrantMode())) {
                    // Role-grant deny mode is documented as reserved; it never grants access.
                    continue;
                }
                if (!CapabilityScopeRules.assignmentAppliesToBranch(assignment, activeBranchId)) {
                    inapplicableRoles.add(roleRef(assignment, roleDefinitionsById));
                    continue;
                }
                CapabilityScopeRules.ResolvedScope resolvedScope =
                        CapabilityScopeRules.resolveScopeForAssignment(grant, assignment);
                if (resolvedScope == null
                        || !CapabilityScopeRules.scopeAppliesToBranch(
                                resolvedScope.getScopeType(), resolvedScope.getScopeBranchId(), activeBranchId)) {
                    // Includes the inert combination: company-scoped assignment of a
                    // branch-scope capability resolves to (branch, null) and can never
                    // apply to any concrete branch context.
                    inapplicableRoles.add(roleRef(assignment, roleDefinitionsById));
                    continue;
                }
                effectiveHits.add(new RoleHit(assignment, resolvedScope));
            }
        }

        // ---- Overrides. At most one override exists per (scopeType[, branch]) key. ----
        TenantUserGrantOverrideConfig applyingAllow = null;
        TenantUserGrantOverrideConfig applyingDeny = null;
        boolean hasOtherContextOverride = false;
        for (TenantUserGrantOverrideConfig override : capabilityOverrides) {
            boolean applies = CapabilityScopeRules.scopeAppliesToBranch(
                    override.getScopeType(), override.getScopeBranchId(), activeBranchId);
            if (!applies) {
                hasOtherContextOverride = true;
                continue;
            }
            if ("deny".equalsIgnoreCase(override.getGrantMode())) {
                if (applyingDeny == null) {
                    applyingDeny = override;
                }
            } else if (applyingAllow == null) {
                applyingAllow = override;
            }
        }

        // ---- Deny suppression: a deny removes exactly the entries sharing its resolved key. ----
        ArrayList<AccessMapRoleRef> grantingRoles = new ArrayList<>();
        ArrayList<AccessMapRoleRef> blockedRoles = new ArrayList<>();
        for (RoleHit hit : effectiveHits) {
            boolean suppressed = applyingDeny != null && CapabilityScopeRules.capabilityMapKey(
                    capability.getCapabilityKey(), hit.scope.getScopeType(), hit.scope.getScopeBranchId())
                    .equals(overrideKey(capability.getCapabilityKey(), applyingDeny));
            if (suppressed) {
                blockedRoles.add(roleRef(hit.assignment, roleDefinitionsById));
            } else {
                grantingRoles.add(roleRef(hit.assignment, roleDefinitionsById));
            }
        }

        boolean directGranted = applyingAllow != null;
        boolean roleGranted = !grantingRoles.isEmpty();
        boolean granted = directGranted || roleGranted;

        // ---- State (first match wins). ----
        String state;
        if (deprecated) {
            state = AccessMapNode.STATE_DEPRECATED;
        } else if (directGranted) {
            state = AccessMapNode.STATE_DIRECT_GRANTED;
        } else if (roleGranted) {
            state = AccessMapNode.STATE_ROLE_GRANTED;
        } else if (applyingDeny != null) {
            state = AccessMapNode.STATE_EXPLICITLY_DENIED;
        } else if (!moduleAvailable) {
            state = AccessMapNode.STATE_MODULE_LOCKED;
        } else if (!inapplicableRoles.isEmpty() || hasOtherContextOverride) {
            state = AccessMapNode.STATE_SCOPE_MISMATCH;
        } else {
            state = AccessMapNode.STATE_NOT_GRANTED;
        }

        AccessMapNode node = new AccessMapNode();
        node.setId("capability:" + capability.getCapabilityKey());
        node.setType("CAPABILITY");
        node.setLabel(humanize(capability.getAction()) + " " + humanize(capability.getResource()));
        node.setModuleId(capability.getModuleId());
        node.setCapabilityKey(capability.getCapabilityKey());
        node.setResource(capability.getResource());
        node.setAction(capability.getAction());
        node.setDescription(capability.getDescription());
        node.setScopeType(capability.getScopeType());
        node.setScopeBranchId("branch".equalsIgnoreCase(capability.getScopeType()) ? activeBranchId : null);
        node.setCapabilityStatus(capability.getStatus());
        node.setModuleAvailable(moduleAvailable);
        node.setAvailabilitySource(effectiveModule != null ? effectiveModule.getSource() : null);
        node.setCapabilityGranted(granted);
        node.setEffectiveAccess(granted);
        node.setUsableAccess(granted && moduleAvailable);
        node.setAccessState(state);
        node.setSource(directGranted ? "user_override" : roleGranted ? "role_grant" : null);
        node.setGrantingRoles(grantingRoles);
        node.setBlockedRoles(blockedRoles);
        node.setInapplicableRoles(inapplicableRoles);
        node.setAllowOverrideId(applyingAllow != null ? applyingAllow.getOverrideId() : null);
        node.setDenyOverrideId(applyingDeny != null ? applyingDeny.getOverrideId() : null);
        node.setOverrideReason(applyingAllow != null ? applyingAllow.getReason()
                : applyingDeny != null ? applyingDeny.getReason() : null);

        applyActionFlags(node, capability, input.canMutate, ownerBypass, moduleAvailable, deprecated,
                directGranted, roleGranted, applyingDeny != null, activeBranchId,
                effectiveModule != null ? effectiveModule.getSource() : null);
        return node;
    }

    private void applyActionFlags(AccessMapNode node,
                                  PlatformCapabilityConfig capability,
                                  boolean canMutate,
                                  boolean ownerBypass,
                                  boolean moduleAvailable,
                                  boolean deprecated,
                                  boolean directGranted,
                                  boolean roleGranted,
                                  boolean denied,
                                  Integer activeBranchId,
                                  String availabilitySource) {
        node.setCanGrant(false);
        node.setCanBlock(false);
        node.setCanRemoveDirectGrant(false);
        node.setCanRestoreRoleAccess(false);

        if (!canMutate) {
            node.setActionBlockedReason(AccessMapNode.REASON_READ_ONLY);
            return;
        }
        if (ownerBypass) {
            node.setActionBlockedReason(AccessMapNode.REASON_OWNER_BYPASS);
            return;
        }
        if (deprecated) {
            node.setActionBlockedReason(AccessMapNode.REASON_CAPABILITY_DEPRECATED);
            return;
        }

        String scopeType = capability.getScopeType() == null
                ? "" : capability.getScopeType().toLowerCase(Locale.ROOT);
        boolean scopeManageable = scopeType.equals("company") || scopeType.equals("self")
                || (scopeType.equals("branch") && activeBranchId != null);
        if (!scopeManageable) {
            // global_admin capabilities (or branch scope without a branch context) cannot be
            // targeted by the tenant override endpoints.
            node.setActionBlockedReason(AccessMapNode.REASON_SCOPE_NOT_APPLICABLE);
            return;
        }

        if (directGranted) {
            node.setCanRemoveDirectGrant(true);
        }
        if (denied) {
            node.setCanRestoreRoleAccess(true);
        }
        if (roleGranted && !directGranted) {
            node.setCanBlock(true);
        }
        if (!directGranted && !roleGranted && !denied) {
            if (moduleAvailable) {
                node.setCanGrant(true);
            } else {
                node.setActionBlockedReason(
                        "package_locked".equalsIgnoreCase(availabilitySource)
                                || "package".equalsIgnoreCase(availabilitySource)
                                ? AccessMapNode.REASON_MODULE_PACKAGE_LOCKED
                                : AccessMapNode.REASON_MODULE_DISABLED);
            }
        }
    }

    private void applyAggregates(AccessMapNode parent, List<AccessMapNode> capabilities) {
        int accessible = 0;
        int locked = 0;
        int denied = 0;
        int deprecated = 0;
        boolean conflict = false;
        for (AccessMapNode capability : capabilities) {
            if (Boolean.TRUE.equals(capability.getUsableAccess())) {
                accessible++;
            }
            if (Boolean.FALSE.equals(capability.getModuleAvailable())) {
                locked++;
            }
            if (capability.getDenyOverrideId() != null) {
                denied++;
                if (capability.getBlockedRoles() != null && !capability.getBlockedRoles().isEmpty()) {
                    conflict = true;
                }
            }
            if (AccessMapNode.STATE_DEPRECATED.equals(capability.getAccessState())) {
                deprecated++;
            }
        }
        int total = capabilities.size();
        parent.setAccessibleCount(accessible);
        parent.setTotalCount(total);
        parent.setLockedCount(locked);
        parent.setDeniedCount(denied);

        String aggregate;
        if (locked == total && total > 0) {
            aggregate = AccessMapNode.AGGREGATE_LOCKED;
        } else if (conflict) {
            aggregate = AccessMapNode.AGGREGATE_CONFLICT;
        } else if (accessible == 0) {
            aggregate = AccessMapNode.AGGREGATE_NONE;
        } else if (accessible >= total - deprecated) {
            aggregate = AccessMapNode.AGGREGATE_FULL;
        } else {
            aggregate = AccessMapNode.AGGREGATE_PARTIAL;
        }
        parent.setAggregateState(aggregate);
    }

    private AccessMapSummary buildSummary(List<AccessMapNode> capabilityNodes) {
        int accessible = 0;
        int direct = 0;
        int role = 0;
        int denies = 0;
        int locked = 0;
        int deprecated = 0;
        int scopeMismatch = 0;
        for (AccessMapNode node : capabilityNodes) {
            if (Boolean.TRUE.equals(node.getUsableAccess())) {
                accessible++;
            }
            if (node.getAllowOverrideId() != null) {
                direct++;
            }
            if (AccessMapNode.STATE_ROLE_GRANTED.equals(node.getAccessState())) {
                role++;
            }
            if (node.getDenyOverrideId() != null) {
                denies++;
            }
            if (Boolean.FALSE.equals(node.getModuleAvailable())) {
                locked++;
            }
            if (AccessMapNode.STATE_DEPRECATED.equals(node.getAccessState())) {
                deprecated++;
            }
            if (AccessMapNode.STATE_SCOPE_MISMATCH.equals(node.getAccessState())) {
                scopeMismatch++;
            }
        }
        return new AccessMapSummary(
                accessible,
                capabilityNodes.size(),
                direct,
                role,
                denies,
                locked,
                deprecated,
                scopeMismatch
        );
    }

    private ArrayList<AccessMapRoleRef> buildAssignedRoles(List<TenantRoleAssignmentConfig> assignments,
                                                           Map<String, RoleDefinitionConfig> roleDefinitionsById) {
        ArrayList<AccessMapRoleRef> refs = new ArrayList<>();
        for (TenantRoleAssignmentConfig assignment : assignments) {
            refs.add(roleRef(assignment, roleDefinitionsById));
        }
        return refs;
    }

    private AccessMapRoleRef roleRef(TenantRoleAssignmentConfig assignment,
                                     Map<String, RoleDefinitionConfig> roleDefinitionsById) {
        RoleDefinitionConfig definition = roleDefinitionsById.get(assignment.getRoleId());
        return new AccessMapRoleRef(
                assignment.getRoleId(),
                definition != null ? definition.getDisplayName() : assignment.getRoleId(),
                assignment.getAssignmentId(),
                assignment.getScopeType(),
                assignment.getScopeBranchId()
        );
    }

    private String overrideKey(String capabilityKey, TenantUserGrantOverrideConfig override) {
        return CapabilityScopeRules.capabilityMapKey(capabilityKey, override.getScopeType(), override.getScopeBranchId());
    }

    private AccessMapEdge hierarchyEdge(String source, String target) {
        return new AccessMapEdge("edge:" + source + "->" + target, source, target, "hierarchy");
    }

    private String displayName(ConfigurationAdminUserSummary user) {
        String name = ((user.getFirstName() == null ? "" : user.getFirstName().trim()) + " "
                + (user.getLastName() == null ? "" : user.getLastName().trim())).trim();
        return name.isEmpty() ? user.getUserName() : name;
    }

    private boolean isOwner(ConfigurationAdminUserSummary user) {
        return user.getLegacyRole() != null && "owner".equalsIgnoreCase(user.getLegacyRole().trim());
    }

    private String humanize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        String[] parts = value.trim().toLowerCase(Locale.ROOT).split("[_\\s]+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    private static final class RoleHit {
        private final TenantRoleAssignmentConfig assignment;
        private final CapabilityScopeRules.ResolvedScope scope;

        private RoleHit(TenantRoleAssignmentConfig assignment, CapabilityScopeRules.ResolvedScope scope) {
            this.assignment = assignment;
            this.scope = scope;
        }
    }
}
