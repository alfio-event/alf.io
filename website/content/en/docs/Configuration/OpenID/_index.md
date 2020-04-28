---
title: "OpenID Integration"
linkTitle: "OpenID Integration"
weight: 5
description: >
  How to configure OpenID
---

## Configuring OpenID

### What you need to provide to alf.io from your IDP

To setup a working OpenID environment, you will need to configure your IDP so that it can pass the following information to alf.io:

- user role
- user permission for the organization

#### User role

This single string attribute is used in order to identify the role of the user, it can only be ALFIO_ADMIN in case the user is the administrator
or it can be ALFIO_BACKOFFICE, in case the user is not the administrator.

You can specify one or both of them for any given user, alf.io will only take into consideration the one with the highest scope (that is ALFIO_ADMIN, if present).

#### User permission for the organization

For each user you will need to specify the organization, and the permission of the user in it. This attribute is an array of strings, specified for each user.

Each string will be structured in the following way: organization/permission (e.g. org1/OPERATOR).

### Populate the fields in the application.properties file

You will need to create a file called "application.properties" in your server folder in order to be able to add the required properties.

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

This is what your application.properties file should look like.

![](/img/configuration/open-id/application-properties.png)