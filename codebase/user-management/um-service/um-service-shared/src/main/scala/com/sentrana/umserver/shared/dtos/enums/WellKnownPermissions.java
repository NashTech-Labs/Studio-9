package com.sentrana.umserver.shared.dtos.enums;

import java.util.Arrays;
import java.util.List;

/**
 * Subset of possible permissions that is going to be used in code regularly enough to have appropriate constants.
 * Doesn't include every possible permission, so please don't convert a string to this enum - it will likely fail
 * on some perfectly valid permissions.
 *
 * Created by Paul Lysak on 20.04.16.
 */
public enum WellKnownPermissions {
    SUPERUSER /* automatically gets all permissions */,
    USERS_GET_DETAILS,
    USERS_SEARCH,
    USERS_CREATE,
    USERS_UPDATE,
    USERS_DELETE,
    USERS_ACTIVATE,
    USERS_DEACTIVATE,
    GROUPS_GET_DETAILS,
    GROUPS_SEARCH,
    GROUPS_CREATE,
    GROUPS_UPDATE,
    GROUPS_DELETE,
    ORGS_GET_DETAILS,
    ORGS_SEARCH,
    ORGS_CREATE,
    ORGS_UPDATE,
    ORGS_DELETE,
    APPS_GET_DETAILS,
    APPS_SEARCH,
    APPS_CREATE,
    APPS_UPDATE,
    APPS_DELETE,
    APPS_REGENERATE,
    APPS_CLIENT_SECRET_GET_DETAILS,
    FILTERS_GET_DETAILS,
    FILTERS_SEARCH,
    FILTERS_CREATE,
    FILTERS_UPDATE,
    FILTERS_DELETE,
    SAML_PROVIDERS_GET_DETAILS,
    SAML_PROVIDERS_SEARCH,
    SAML_PROVIDERS_CREATE,
    SAML_PROVIDERS_UPDATE,
    SAML_PROVIDERS_DELETE;

    public static List<WellKnownPermissions> getReadOnlyPermissions() {
        return readOnlyPermissions;
    }

    private static List<WellKnownPermissions> readOnlyPermissions = Arrays.asList(
            USERS_GET_DETAILS,
            USERS_SEARCH,
            GROUPS_GET_DETAILS,
            GROUPS_SEARCH,
            ORGS_GET_DETAILS,
            ORGS_SEARCH,
            APPS_GET_DETAILS,
            APPS_SEARCH,
            FILTERS_GET_DETAILS,
            FILTERS_SEARCH,
            SAML_PROVIDERS_GET_DETAILS,
            SAML_PROVIDERS_SEARCH);
}
