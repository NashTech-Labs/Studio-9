
db.organization.find({"name": "SelfService"}).
map(function(d) {
    return d.users.filter(function(u) {return u.status == "A";}).
    map(function(u) {return {id: u.id,
        username: u.userName,
        email: u.email,
        password: u.password,
        firstName: u.firstName,
        lastName: u.lastName,
        password: u.password,
        created: u.createDate,
        updated: u.createDate,
        organizationId: "orgs_self_service",
        status: "ACTIVE",
        dataFilterInstances: [],
        groupIds: []
    }
  });
})
