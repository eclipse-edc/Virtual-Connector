# Access Control in EDC-V

This document is to outline the requirements for access control in all APIs in EDC-V and its associated components
IdentityHub, IssuerService and the Data Plane.

## Authentication

OAuth2 is used for authentication. Clients are supposed to authenticate with the IdP and obtain a JWT token using the
`client_credentials` flow.

EDC-V uses a centralized identity provider (IdP) for all APIs; that means that the same ID and secret are used for all
resources.

It is **not recommended** to use the same ID for the participant context `participantContextId` and OAuth2 `client_id`.

## Authorization

Authorization is done centrally using JWT tokens. The IdP will issue JWT tokens to clients that are authorized to access
resources by specifying the required scopes in the token request.

Access control is generally done using custom token claims.

## Custom token claims

JWT tokens are used to authenticate users and to authorize access to resources. In addition to the standard claims as
defined in [RFC 7519](https://tools.ietf.org/html/rfc7519), the following claims are required:

- `role`: this must contain the role of the client that is requesting access. APIs may choose to authorize solely based
  on the role, or additionally on the scope. For example, `admin` users may not even need scopes, as all scopes are
  granted implicitly.
- `scope`: contains a space-separated list of scopes that the user is authorized to access, for example `"scope":
"management-api:read management-api:write"`. For further information, see chapter [Scopes](#scopes)
- `participant_context_id`: this is the identifier of the participant context that is used throughout the EDC-V
  ecosystem to identify a participant. This may be different from the `client_id`.

## Scopes

In EDC-V, scopes are used to identify the resource to which a user has access, and the level of access. Unfortunately,
[RFC-8707](https://datatracker.ietf.org/doc/html/rfc8707) is not yet fully supported by all major IdPs.

Scopes used in EDC-V are:

- `management-api:read`: allows read access to the control plane's management API
- `management-api:write`: allows write access to the control plane's management API
- `data-plane:read`: allows read access to the data plane's management API
- `data-plane:write`: allows write access to the data plane's management API
- `identity-api:read`: allows read access to IdentityHub's identity API
- `identity-api:write`: allows write access to IdentityHub's identity API
- `issuer-admin-api`: allows full access to the IssuerService's Issuer Admin API. A distinction between read and write
  access is not required here, as this scope is only granted to provisioner users.

These scopes can be requested by a client making a token request to the IdP by including the `scope` parameter in the
request:

```shell
 curl -X POST $IDP_URL/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=$CLIENT_ID" \
  -d "scope=management-api:write management-api:read" \
  -d "client_secret=$CLIENT_SECRET"
```

## Role definitions

Roles are used to differentiate different types of users in the EDC-V ecosystem. There are three roles defined in EDC-V:

- `admin`: has full access to all resources in all APIs. This should **only** be used by human actors to correct
  errors or fix problems, **never** be used by an automated system!~~
- `tenant-mgr`: can manage participant contexts, but cannot access resources of an individual participant context.
  Specifically, an `tenant-mgr` may create participant contexts in the Control Plane and in IdentityHub and may
  create `Holder` entities in the IssuerService. However, they may not access resources of an individual participant
  context such as Assets, ContractDefinitions, etc. Clients with the `edc-provisioner` role can also create new clients
  in the IdP, for example, in KeyCloak that would require having the `realm-admin` or `manage-clients` role.
- `participant`: this is the role that all new clients are assigned. Clients with this role can access their own
  resources in the individual APIs, but cannot manipulate administrative resources (i.e. `participantContext` resources)
  or resources of other participants.
