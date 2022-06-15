


## Errors
Error status and other info is placed in the response body:  
```
{
  "error": {
    code: 400,
    reason: "%Exception code%", # for example: "ValidationError"
    message: "Something goes wrong :(",
    errors: [{}, ...]
  }
}
```


## Principles
- All "crud" endpoints (such as `POST /flows` or `PUT /table/:id`)
always return the object (except in case of error).
- List endponts (root level endpoints for each entities, for ex. `GET /flows`, `GET /tables`) 
return paginated list of objects.
- Deleting or cancelling crud endpoints return the object ID
- Nested list endpoints (ex. `GET /flows/:id/tables`, `GET /me/flows`)
return non-paginated list of objects (basically all fitted objects).
- Nesting of crud endpoints is highly undesirable (exception is made for `Flowstep` entity only)
- All crud, including list, endpoints prefilter objects based on current user permissions
(if object is not accessible to the user, it will be not included to lists (for list endpoints) or "not found" response will be generated).
- Property names should be underscored, not camelcased 
(how to convert on the fly [for java](http://stackoverflow.com/questions/10519265/jackson-overcoming-underscores-in-favor-of-camel-case) 
and [for js](https://github.com/sindresorhus/camelcase)). 


## Authorization

Token based authorization is used, according to the [RFC 6750](http://tools.ietf.org/html/rfc6750). Token should be passed in the header, query or body. Example of passing token in the header (with the "service" word "Bearer"):
```
Authorization: Bearer 550ab235d5598d5efac0334b
```

Extra two-factor authentication (2fa) token could be passed (experimental):
```
Insilico-2FA-Authorization: Bearer 7a3b8943050334022418249b33ca350d402c4fc3
```


## Filtering

Standard params that are accessible for all the "list" endpoints (RESERVED):
```
PARAMS:
"order"                     : String # example: 'name'
"page"                      : Int # page number # starts from 0
"page_size"                 : Int # records per page # default: 30
"search"                    : String # for free-form search (*)
```


## Authentication

**POST /signup**  
signup user
```
PARAMS:
"firstName"                 : String # required
"lastName"                  : String # required
"email"                     : String # required
"password"                  : String # required

RETURNS:
User object*
* with nested profiles: company, ...
```


**POST /signin**  
login user and send back authorization token
```
PARAMS:
"email"                     : String # required
"password"                  : String # required

RETURN:
- isn't logged:
Error object

- is logged:
"access_token"              : String # required
```


Full list of endpoints and more are accessible in the API explorer  
(see link above).
