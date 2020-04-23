---
title: "OpenID Integration"
linkTitle: "OpenID Integration"
weight: 5
description: >
  How to configure OpenID
---

## Configuring OpenID

### Populate the fields in the application.properties file

you will need to modify the [application.properties](https://github.com/alfio-event/alf.io/blob/master/src/main/resources/application.properties) file in order to be able to add the required properties.

##### Properties:

- 'openid.domain' will contain the name of your domain (e.g. mydomain.com).
- 'openid.clientId' will contain your client id.
- 'openid.clientSecret' will contain your client secret.

- 'openid.authenticationUrl' will contain the url that needs to be called in order to authenticate your users using your domain (e.g. /auth/openid-connect/auth).
- 'openid.claimsUrl' will contain the url to be called to get your claims, specifically the id token (e.g. /auth/openid-connect/token).
- 'openid.logoutUrl' will contain the url to be called to log out a user from your identity provider (e.g. /auth/openid-connect/logout).

- 'openid.callbackURI' is the url in which the user will be redirected after an authentication (e.g. http://localhost:8080/callback).
- 'openid.logoutRedirectUrl' is the url in which the user will be redirected after a logout (e.g. http://localhost:8080/admin).

- 'openid.groupsNameParameter' will contain the roles of the user (ALFIO_ADMIN, ALFIO_BACKOFFICE or both).
- 'openid.alfioGroupsNameParameter' will contain the organization and the role of the user in the organization (e.g. organization1/SPONSOR)

- 'openid.contentType' will contain the content type of the response that returns the claims (application/x-www-form-urlencoded or application/json)
