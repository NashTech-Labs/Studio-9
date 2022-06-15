// default admin

exports.migrate = function(client, done) {
    var baileAddress = process.env.BAILE_LB_URL;
    var baileApiPath = process.env.BAILE_API_PATH;
    var db = client.db;
    var users = db.collection('users');
    var applications = db.collection('applications');

    applications.update(
        {"id": "baile_app_id", "emailConfirmationUrl": { $exists : false }},
        {$set: { "emailConfirmationUrl": "http://" + baileAddress + "/emailconfirmation"}});

    //Default admin user knoldususa_admin password: adminadmin
    //!!!CHANGE DEFAULT PASSWORD IN PROD IMMEDIATELY AFTER FIRST DEPLOY!!!
    users.insert([{
        "id": "77165ba5-e160-464c-aefd-de1aff85a6f2",
        "username": "knoldususa_admin",
        "email" : "no_such_user@sentrana.com",
        "password" : "2710:cW0ybTZwb3V3NzVzZnZlc2M0MDRqbTll:yHaLeC341mnlp7KHZTT0A5vlUUzNF8TO",
        "firstName" : "knoldususa",
        "lastName" : "Admin",
        "status" : "ACTIVE",
        "groupIds": ["groups_superuser"],
        "dataFilterInstances": [],
        "organizationId": "orgs_knoldususa",
        "created": "2019-08-27T07:44:30.385Z[GMT]",
        "updated": "2019-08-27T07:44:30.385Z[GMT]"
    }],done);
};

exports.rollback = function(client, done) {
    var db = client.db;
    var users = db.collection('users');

    users.remove({ "groupIds" : ["groups_superuser"] }, done);
}