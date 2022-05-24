//TODO groups, default admin, indexes

exports.migrate = function(client, done) {
    var db = client.db;
    var orgs = db.collection('organizations');
    var groups = db.collection('userGroups');
    var users = db.collection('users');
    var applications = db.collection('applications');

    orgs.insertMany([{
            "id": "orgs_deepcortex",
            "name" : "DeepCortex",
            "desc" : "DeepCortex",
            "parentOrganizationId": null,
            "status": "ACTIVE",
            "applicationIds": [],
            "dataFilterInstances": [],
            "created": "2016-05-06T00:00:00.000Z",
            "updated": "2016-05-06T00:00:00.000Z",
            "signUpEnabled": false,
            "signUpGroupIds": []
        },
        {
            "id": "orgs_self_service",
            "name" : "SelfService",
            "desc" : "Self-service users",
            "parentOrganizationId": "orgs_deepcortex",
            "status": "ACTIVE",
            "applicationIds": [],
            "dataFilterInstances": [],
            "created": "2016-06-14T00:00:00.000Z",
            "updated": "2016-06-14T00:00:00.000Z",
            "signUpEnabled": true,
            "signUpGroupIds": []
        }], done);

    groups.insertMany([{
        "id": "groups_superuser",
        "organizationId": "orgs_deepcortex",
        "parentGroupId": null,
        "name": "Superusers",
        "description": "Grants all permissions inside of user's organization and its sub-organizations",
        "grantsPermissions": [{"name": "SUPERUSER"}],
        "dataFilterInstances": [],
        "forChildOrgs": true,
        "created": "2016-05-06T00:00:00.000Z",
        "updated": "2016-05-06T00:00:00.000Z"
    }, {
        "id": "groups_read_only_um",
        "organizationId": "orgs_deepcortex",
        "parentGroupId": null,
        "name": "Read-only UM",
        "description": "Grants all known read-only permissions for user management",
        "grantsPermissions": [
            {"name": "USERS_GET_DETAILS"},
            {"name": "USERS_SEARCH"},
            {"name": "GROUPS_GET_DETAILS"},
            {"name": "GROUPS_SEARCH"},
            {"name": "ORGS_GET_DETAILS"},
            {"name": "ORGS_SEARCH"},
            {"name": "APPS_GET_DETAILS"},
            {"name": "APPS_SEARCH"}],
        "dataFilterInstances": [],
        "forChildOrgs": true,
        "created": "2016-05-06T00:00:00.000Z",
        "updated": "2016-05-06T00:00:00.000Z"
    }])

    //Default admin login/password: root/root
    //Default deepcortex login/password: deepcortex/password
    //!!!CHANGE DEFAULT PASSWORD IN PROD IMMEDIATELY AFTER FITST DEPLOY!!!
    users.insertMany([{
        "id": "users_deepcortex_root",
        "username": "root",
        "email" : "no_such_user@sentrana.com",
        "password" : "2710:cm9vdFNhbHQ=:+y1NlIknpwZJO787hByDw2bMlXnXurpd",
        "firstName" : "DeepCortex",
        "lastName" : "Root",
        "status" : "ACTIVE",
        "groupIds": ["groups_superuser"],
        "dataFilterInstances": [],
        "organizationId": "orgs_deepcortex",
        "created": "2016-05-06T00:00:00.000Z",
        "updated": "2016-05-06T00:00:00.000Z"
    }, {
        "id": "users_deepcortex",
        "username": "deepcortex",
        "email" : "support@deepcortex.ai",
        "password" : "2710:cjJsbWRuM21pOTN1cGR0ZjNqcmU3MWww:3bYF4yDzPNLBJrfC6SUvWBm2YwFqElj2",
        "firstName" : "DeepCortex",
        "lastName" : "User",
        "status" : "ACTIVE",
        "groupIds": ["groups_read_only_um"],
        "dataFilterInstances": [],
        "organizationId": "orgs_deepcortex",
        "created": "2016-05-06T00:00:00.000Z",
        "updated": "2016-05-06T00:00:00.000Z"
    }, {
        "id": "16535bc2-c4cf-11e7-abc4-cec278b6b50a",
        "username": "default_user",
        "email" : "default@email.com",
        "password" : "2710:cjJsbWRuM21pOTN1cGR0ZjNqcmU3MWww:3bYF4yDzPNLBJrfC6SUvWBm2YwFqElj2",
        "firstName" : "default",
        "lastName" : "user",
        "status" : "ACTIVE",
        "groupIds": ["groups_read_only_um"],
        "dataFilterInstances": [],
        "organizationId": "orgs_deepcortex",
        "created": "2016-05-06T00:00:00.000Z",
        "updated": "2016-05-06T00:00:00.000Z"
    }])

    applications.insertMany([{
        "id": "baile_app_id",
        "name": "DeepCortex",
        "desc": "DeepCortex",
        "url": "https://dev.deepcortex.ai",
        "passwordResetUrl": "https://dev.deepcortex.ai/signin/password/complete",
        "clientSecret": "baileDevSecret",
        "created": "2016-05-06T00:00:00.000Z",
        "updated": "2016-05-06T00:00:00.000Z"
    }])

    orgs.createIndex({"id": 1}, {"unique": true})
    orgs.createIndex({"status": 1})
    groups.createIndex({"id": 1}, {"unique": true})
    groups.createIndex({"organizationId": 1,
        "parentGroupId": 1,
        "forChildOrgs": 1})
    users.createIndex({"id": 1}, {"unique": true})
    users.createIndex({"organizationId": 1,
        "username": 1,
        "email": 1,
        "status": 1,
        "groupIds": 1})
};

exports.rollback = function(client, done) {
    var db = client.db;
    var orgs = db.collection('organizations');
    var groups = db.collection('userGroups');
    var users = db.collection('users');

    orgs.drop(done);
    groups.drop()
    users.drop()
    applications.drop()
}