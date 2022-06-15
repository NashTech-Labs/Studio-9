//TODO groups, default admin, indexes

exports.migrate = function(client, done) {
    var baileAddress = process.env.BAILE_LB_URL;
    var db = client.db;
    var orgs = db.collection('organizations');
    var groups = db.collection('userGroups');
    var users = db.collection('users');
    var applications = db.collection('applications');

    orgs.insertMany([{
            "id": "orgs_knoldususa",
            "name" : "knoldususa",
            "desc" : "knoldususa",
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
            "parentOrganizationId": "orgs_knoldususa",
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
        "organizationId": "orgs_knoldususa",
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
        "organizationId": "orgs_knoldususa",
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

    //Default knoldususa_user1 login/password: knoldususa/password
    //Default knoldususa_user2 login/password: knoldususa/password
    //!!!CHANGE DEFAULT PASSWORD IN PROD IMMEDIATELY AFTER FITST DEPLOY!!!
    users.insertMany([{
        "id": "02b6451e-dc3c-11e7-9296-cec278b6b50a",
        "username": "knoldususa_user1",
        "email" : "no_such_user@knoldususa.com",
        "password" : "2710:cjJsbWRuM21pOTN1cGR0ZjNqcmU3MWww:3bYF4yDzPNLBJrfC6SUvWBm2YwFqElj2",
        "firstName" : "knoldususa",
        "lastName" : "User1",
        "status" : "ACTIVE",
        "groupIds": ["groups_read_only_um"],
        "dataFilterInstances": [],
        "organizationId": "orgs_knoldususa",
        "created": "2016-05-06T00:00:00.000Z",
        "updated": "2016-05-06T00:00:00.000Z"
    }, {
        "id": "02b6478a-dc3c-11e7-9296-cec278b6b50a",
        "username": "knoldususa_user2",
        "email" : "support@knoldususa.com",
        "password" : "2710:cjJsbWRuM21pOTN1cGR0ZjNqcmU3MWww:3bYF4yDzPNLBJrfC6SUvWBm2YwFqElj2",
        "firstName" : "knoldususa",
        "lastName" : "User2",
        "status" : "ACTIVE",
        "groupIds": ["groups_read_only_um"],
        "dataFilterInstances": [],
        "organizationId": "orgs_knoldususa",
        "created": "2016-05-06T00:00:00.000Z",
        "updated": "2016-05-06T00:00:00.000Z"
    }])

    applications.insertMany([{
        "id": "baile_app_id",
        "name": "knoldususa",
        "desc": "knoldususa",
        "url": "http://" + baileAddress,
        "passwordResetUrl": "http://" + baileAddress + "/signin/password/complete",
        "clientSecret": "baileDevSecret",
        "created": "2016-05-06T00:00:00.000Z",
        "updated": "2016-05-06T00:00:00.000Z"
    }])

    orgs.ensureIndex({"id": 1}, {"unique": true})
    orgs.ensureIndex({"status": 1})
    groups.ensureIndex({"id": 1}, {"unique": true})
    groups.ensureIndex({"organizationId": 1,
        "parentGroupId": 1,
        "forChildOrgs": 1})
    users.ensureIndex({"id": 1}, {"unique": true})
    users.ensureIndex({"organizationId": 1,
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