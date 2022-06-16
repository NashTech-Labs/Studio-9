um-client-sdk-play
==================

SDK for building Play applications that rely on um-service for user management, authentication and authorization.
Requires Play version 2.4 or later. Some features (like `UmForwardingController`) rely on injected routes generator.
[Acceptance tests](../um-client-sdk-play-acceptance) include sample application that illustrates how to use the SDK.

# Adding SDK to the project

First of all you need to add Sentrana Nexus artifactory to resolvers:

    resolvers += "Sentrana Releases" at "http://10.46.34.94:8081/nexus/content/repositories/releases"
    
It requires VPN. Alternatively, you can build SDK locally and publish it to local repository, but this approach won't be covered here.

Then you'll need a dependency to SDK:

   libraryDependencies += "com.sentrana" %% "um-client-sdk-play" % "0.0.2" 
   
Now you can either manually instantiate `UmClient` (main entry point to um-service), 
or rely on Guice (Play's dependency injection framework) to create and inject it.
In order to configure it with `Guice` you need to have a module. This way you can enable a module and
provide some config for `UmClient` in Play's config file:
 
    play.modules.enabled += "my.proj.MyModule"
    sentrana.um.server.url=http://um-service.somewhere.com
    sentrana.um.server.client.id=myClient
    sentrana.um.server.client.secret=test

`url` here contains location of `um-service` to use, 
and `client.id`/`client.secret` refer to application credentials - id and clientSecret in um-service.

Then you'll need to implement the module:

    package my.proj 

    import com.google.inject.{ Scopes, AbstractModule }
    import com.sentrana.um.client.play.{ UmClient, UmClientImplProvider }

    class MyModule extends AbstractModule {
      override def configure(): Unit = {
        bind(classOf[UmClient]).toProvider(classOf[UmClientImplProvider]).in(Scopes.SINGLETON)
      }
    }
   
Then you can inject `UmClient` into your services/controllers:

    @Singleton
    class SampleService @Inject() (val umClient: UmClient) {
        ...
    }

Let's take a look at typical tasks which SDK helps to solve

# User sign in/sign out

You may either capture username and password and call `UmClient.signIn`/`UmClient.signOut`, or configure proxy controller 
`UmForwardingController` and have REST API for sign in/sign out available to your UI. Add following lines to your routes file:

    POST      /token             com.sentrana.um.client.play.UmForwardingController.signIn
    DELETE    /token/:token      com.sentrana.um.client.play.UmForwardingController.signOut(token)

and sign in/sign out requests will be directly forwarded to um-service. Sign In endpoint follows 
(OAuth 2 password credentials grant)[https://tools.ietf.org/html/draft-ietf-oauth-v2-31#page-35],
therefore it expects `application/x-www-form-urlencoded` request with following standard fields:

- grant_type = "password"
- username
- password

and non-standard fields:

- set_cookie - optional. If set to "true" - sets "access_token" cookie to access token value. Therefore, all subsequent
  requests to the application may be authenticated (if cookie handling is enabled for application or this specific endpoint - to be described later)
- organization_id - limits sign in to users of specific organization (without sub-organizations)

Response is JSON object with fields "access_token" and "expires_in".

# Authentication 

In simplest case you can manually validate access token with `UmClient.validateAccessToken(token: String): Future[Option[User]]`. 
But more productive approach involves `SecuredController` base trait. 
Having extended from it and injected UmClient (it's required for SecuredController):

    @Singleton
    class SampleController @Inject() (val umClient: UmClient) extends SecuredController { ... }
    
you can use SecuredAction:

    def myAction = SecuredAction { req =>
      Ok("{}")
    }
    
`myAction` body will be executed only if request contains valid access token, either in query parameter "access_token",
or in cookie "access_token", or in header "Authorization" like "Bearer 123412341234". Otherwise HTTP code 401 will be returned. 

# Authorization 

`SecuredAction` takes optional parameter `authorization` of type `type Authorization = (User, Request[Any]) => Future[Boolean]`.
So far SDK provides the only implementation of it - permission-based with optional org scope. It can be instantiated with method
`def RequirePermission(permission: String, scopeOrgId: Option[String] = None): Authorization` of `SecuredController`.
Simplest example of usage:

    def anotherAction = SecuredAction(RequirePermission("SAMPLE_PERMISSION")) { req =>
      Ok("{}")
    }

Besides requiring user to be authenticated, it requires user to have permission "SAMPLE_PERMISSION" and belong to root organization.
Permission is a free-form string which can be interpreted by apps the way they want it. 
Typically permission identifies fine-grained piece of functionality - create an item, search an item, etc. - the smallest assignable thing.
There's a special type of permission "SUPERUSER" which grants all other permissions. 
In order to have specific permission user has to be member of group that grants that permission or its 
subgroup - groups may build a hierarchy with child groups inheriting permission from parents. Group may be inherited by groups from child orgs
if flag forChildOrgs is set.

If you want allow users from any org but only with specific permission to use your action 
(pretty strange thing to want, I hope you know what are you doing  - users of one org will be able to change other org's user data) 
you may use following code:

    def forAllOrgsAction = SecuredAction(RequirePermission("SAMPLE_PERMISSION", None)) { req =>
      Ok("{}")
    }
    
If you want users from root org or from specific org to access your action (probably, most frequent case in our multi-tenant environment): 

    def orgScopedAction(orgId: String) = SecuredAction(RequirePermission("SAMPLE_PERMISSION", Some(orgId)) { req =>
      val filteredData = //filter your data using orgId as a "scope"
      Ok(filteredData)
    }
    
You'll need to capture orgId somehow - either from path or from query parameter. Having ensured by `RequirePermission` that user has 
access to org orgId you should use that orgId to restrict data inside the action to data of orgId or its child org.
Orgs make a hierarchy (currently 2-level - 1 org is root, and all others - leaf orgs) and orgId here can be understood 
as a "scope" - a bit resembling scope of variables in programming languages, but reverse: 
if data belongs to root org it can be accessed by users from root org. If it belongs to child org - by users from root org or that specific child org.
It's not like orgScopedAction can operate only on data belonging to orgId, it can belong to child orgs if orgId is root.
And when making request user states which scope does he want to access, thus specifying orgId. Root org user may specify orgId of any child org
while regular org user may only specify his own org, otherwise he'll get HTTP 403 response.
Might seem complicated at first, but this approach makes reusable permission check code in multi-tenant system possible.

If capabilities of `SecuredAction` don't fit you needs you have 2 options:

- Custom implementation of `BaseSecuredController.Authorization` 
- Restrict access in action body code

# Stub - testing app without um-service

 You don't need a running um-service in order to run tests for your app that relies on um-service. 
 You can substitute `UmClientStub` instead of actual `UmClient` implementation in your Play application and manipulate
 all user information locally. Having such declaration of your app's spec:
 
    class MyAppSpec extends PlaySpec with OneServerPerSuite with BeforeAndAfterAll { ... }
    
  you can tweak creating test app this way:
 
      private val umClientStub = new UmClientStub
      
      implicit override lazy val app = new GuiceApplicationBuilder()
        .in(Mode.Test)
        .overrides(bind[UmClient].toInstance(umClientStub)) 
        ... //other calls such as .global(...) or .config(...) may apply here
        .build
  
  then in the test setup code you may write: 
  
      private val user1 = umClientStub.addUser(username = "myFirstUser")
      private val user1token = umClientStub.issueToken(user1)
      
  and then you'll be able to use `user1token` during the calls to your applications - as part of `Authorization` header 
  or as `access_token` query parameter. There are also other parameters in user creation method, and there are some other 
  useful methods - for example, group manipulation.

# Cookies and access token

By default access token handling via cookies is disabled. The behaviour can be changed either for a specific controller or a specific action.
In you want to change the behaviour for a controller, you should override authCookieEnabled in the controller:

    override protected def authCookieEnabled: Boolean = true

In case of changing behaviour in a specific action you should specify `authCookieEnabled` parameter in SecuredAction:

     def myAction = SecuredAction(Option(true)) { req =>
       Ok("{}")
     }

The same can be done if an action requires a permission validation:

 def anotherAction = SecuredAction(RequirePermission("SAMPLE_PERMISSION"), Option(true)) { req =>
     Ok("{}")
 }

Keep in mind that action specific behaviour has higher priority than the controller one.

# Authentication with SAML

TODO: finish implementation and describe

# Out of scope

Sign up and org/group/user/filters/apps administration are not considered to be responsibility of applications that use the SDK, 
 it's responsibility of um-service and its future UI. Therefore, client SDK will not support it.
