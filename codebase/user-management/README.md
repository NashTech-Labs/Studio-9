# UM-Service - user management, authentication and authorization #

## Running tests

Then you can run different tests. 
Run all possible tests, starting from lightweight to heavyweight, aborting on first failed test:

    toolbelt/testEverything.sh

Run unit tests for umServer: (almost nothing yet):

    sbt umServer/test

Run integration tests (main heavy-lifting is here):

    sbt umServer/it:test
    
Run acceptance tests for current version of client against current version of server 
(not much there now, will be subject of active development in nearest future):

    sbt publishLocal
    sbt acceptance
    
Run current version of um-service with embedded MongoDB on port 9000 (useful for running individual acceptance tests in IDE):

    sbt publishLocal
    sbt ";project umServiceTestLauncher; launch 9000"
    
or using prepared shell-script:
    
    sbt publishLocal
    toolbelt/umServiceTestLauncher.sh

Running individual integration tests in IDE from `um-service` requires `-Dconfig.resource=integration.conf` JVM argument,
and from `um-client-sdk-play-acceptance`  - `-Dconfig.resource=acceptance.conf`. Otherwise test application can't find proper routes
and modules and you'll see weird error messages.

TODO: describe sentrana.um.server.url and play.mailer.port for running tests with IDE, they're actually required.

## Cross-version acceptance tests

It is possible to run acceptance tests of one verion of um-client-sdk-play (typically older one) 
against different version of um-service (typically newer one). This might be needed to make sure that new version of server
doesn't break existing apps that use older version of client. You may check out older version of the project by tag and then run:

    sbt acceptance -DumServerVersion=0.0.2
    
to test it against version 0.0.2 of um-service. Required version will be retrieved from local artifactory or from Sentranas Nexus.
You may also launch specific version of um-service without automatically running tests - for example, if you'd like to run 
individual tests in IDE:

    sbt ";project umServerTestLauncher; launch 9000" -DumServerVersion=0.0.2


## Opening project in IDE

When importing the project for the first time (or right after release) you may see complains about unresolved dependencies - something like
`com.sentrana#um-service_2.11;X.X.X-SNAPSHOT: not found`. 
That's because `um-client-play-acceptance` retrieves `um-service` from artifactory because it should be able to test against
 arbitrary version of server, not only current one. In order to have `um-service` in your local artifactory you need to run 
 `sbt publishLocal`. If you run all tests with `toolbelt/testEverything.sh` then it will be done behind the scenes. 

## Sample data, running app

DB migrations in `um-service/conf/migrations` folder create indexes and minimal necessary data - single root org, 2 groups (read-only access to everything 
and superuser privileges), 2 users belonging to those groups. Default root org admin login/password  is root/root 
(!!!Remember to change it on prod right after deploy!!!).
In order to apply these migrations to local MongoDB on default port:

    ./toolbelt/migrateLocalMongo.sh

Then you can run um-service:

    sbt umService/run
    
And check if default root user can log in:
 
    toolbelt/curl/getRootToken.sh
    
## Releasing new version

You need Nexus credentials configured in `<your home directory>/.ivy2/.credentials`:

    realm=Sonatype Nexus Repository Manager
    host=10.46.34.94
    user=<your username>
    password=<your password>
    
Then you can release the project (uses <https://github.com/sbt/sbt-release> plugin - read docs there for more details):

    sbt release
  
It will ask you for release version and next snapshot version, provided defaults are OK - you can just press enter.
After release it will propose you to push changes to remote git repository.

## Integrating into other applications

Play applications can use [umClientSdkPlay](um-client-sdk-play) library to get access to umServer without manually making HTTP request.

## SAML

How to log in with SAML provider:
//TODO this sequence hasn't yet completed successfully with actual SAML providers, work in progress both in implementation and in documentation

1. Make sure SAML provider is configured in Mongo DB. For test purposes there are json files in `toolbelt/saml_test_local` -
put them into `samlProviders` collection in Mongo DB with your favorite tool.
Later on there should be administration UI to configure SAML providers.
2. Open https://um-service-dev.sentrana.com/api/um-service/v0.1/saml/starterTest for login test page - should be replaced
by proper UI later. On current test page you can manually enter the name of application where you'd like to be redirected afterwards.
3. Enter credentials of your user on SAML server
4. If it's the first time this user logs in with SAML - you'll be redirected to the page that captures information required for account creation -
username, email etc.
5. Now you'll be redirected to the application entered on step 2

## REST API Swagger spec

If configuration property `umserver.swagger.enabled` is set to `true`(as it is on dev env https://um-service-dev.sentrana.com/),
 um-service generates Swagger spec for its API.
The spec is available at (your server location)/api/um-service/v0.1/docs/swagger.json .

You may use [Soap UI](https://www.soapui.org/) with [Swagger plugin](https://github.com/SmartBear/readyapi-swagger-plugin)
in order to perform some actions in um-service. You'll need to save Swagger spec to local file in order to import it 
into Soap UI, as there are some problems with directly reading spec via HTTPS.

[Swagger UI](http://swagger.io/swagger-ui/) also can be used to explore API after saving Swagger spec locally, but 
it's not that easy to make API calls due to cross-domain issues. You'll need to construct some kind of proxy that serves
both Swagger UI and um-service API and then modify Swagger spec accordingly.