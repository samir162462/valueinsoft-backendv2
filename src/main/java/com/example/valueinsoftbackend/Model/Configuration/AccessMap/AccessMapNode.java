package com.example.valueinsoftbackend.Model.Configuration.AccessMap;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

/**
 * Flat, type-discriminated node of the user access map.
 *
 * <p>Types: USER, MODULE, RESOURCE, CAPABILITY. Fields not relevant to a node's
 * type stay null and are omitted from JSON.</p>
 *
 * <p>Node id scheme (stable, branch-independent because the whole map is already
 * resolved for one (tenant, branch) context):</p>
 * <pre>
 *   user:{userId}
 *   module:{moduleId}
 *   resource:{moduleId}:{resource}
 *   capability:{capabilityKey}
 * </pre>
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AccessMapNode {

    // Access states for CAPABILITY nodes.
    public static final String STATE_ROLE_GRANTED = "ROLE_GRANTED";
    public static final String STATE_DIRECT_GRANTED = "DIRECT_GRANTED";
    public static final String STATE_EXPLICITLY_DENIED = "EXPLICITLY_DENIED";
    public static final String STATE_NOT_GRANTED = "NOT_GRANTED";
    public static final String STATE_MODULE_LOCKED = "MODULE_LOCKED";
    public static final String STATE_SCOPE_MISMATCH = "SCOPE_MISMATCH";
    public static final String STATE_DEPRECATED = "DEPRECATED";

    // Aggregate states for MODULE / RESOURCE nodes.
    public static final String AGGREGATE_FULL = "FULL";
    public static final String AGGREGATE_PARTIAL = "PARTIAL";
    public static final String AGGREGATE_NONE = "NONE";
    public static final String AGGREGATE_LOCKED = "LOCKED";
    public static final String AGGREGATE_CONFLICT = "CONFLICT";

    // Reasons a management action is unavailable on a capability node.
    public static final String REASON_MODULE_PACKAGE_LOCKED = "MODULE_PACKAGE_LOCKED";
    public static final String REASON_MODULE_DISABLED = "MODULE_DISABLED";
    public static final String REASON_CAPABILITY_DEPRECATED = "CAPABILITY_DEPRECATED";
    public static final String REASON_OWNER_BYPASS = "OWNER_BYPASS";
    public static final String REASON_READ_ONLY = "READ_ONLY";
    public static final String REASON_SCOPE_NOT_APPLICABLE = "SCOPE_NOT_APPLICABLE";

    private String id;
    private String parentId;
    /** USER | MODULE | RESOURCE | CAPABILITY */
    private String type;
    /** Server-generated fallback label; the frontend overlays translations when available. */
    private String label;

    // ---- MODULE fields ----
    private String moduleId;
    private String category;
    /** platform_default | package | package_locked | template | tenant_override */
    private String availabilitySource;
    /** active | experimental | deprecated | retired */
    private String moduleStatus;
    private Boolean moduleAvailable;
    /** standard | read_only | hidden */
    private String moduleMode;

    // ---- MODULE + RESOURCE aggregates (from the FULL capability set) ----
    private Integer accessibleCount;
    private Integer totalCount;
    private Integer lockedCount;
    private Integer deniedCount;
    private String aggregateState;

    // ---- CAPABILITY fields ----
    private String capabilityKey;
    private String resource;
    private String action;
    private String description;
    private String scopeType;
    /** Concrete branch the state was resolved for (branch-scoped capabilities). */
    private Integer scopeBranchId;
    /** active | deprecated */
    private String capabilityStatus;
    private Boolean capabilityGranted;
    private Boolean effectiveAccess;
    private Boolean usableAccess;
    private String accessState;
    /** role_grant | user_override | null */
    private String source;
    /** Every role assignment whose grant currently contributes effective access. */
    private ArrayList<AccessMapRoleRef> grantingRoles;
    /** Role assignments whose grant is suppressed by the deny override. */
    private ArrayList<AccessMapRoleRef> blockedRoles;
    /**
     * Role assignments holding a grant for this capability that can never become
     * effective in any branch context (for example a company-scoped assignment of a
     * branch-scope capability) or that apply only to other branches.
     */
    private ArrayList<AccessMapRoleRef> inapplicableRoles;
    private Long allowOverrideId;
    private Long denyOverrideId;
    private String overrideReason;
    private Boolean canGrant;
    private Boolean canBlock;
    private Boolean canRemoveDirectGrant;
    private Boolean canRestoreRoleAccess;
    private String actionBlockedReason;
}
