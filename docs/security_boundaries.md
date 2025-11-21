# Security Boundaries in EDC-V

## Overview

As with many multi-tenant systems, it is important to establish a clear separation between individual users to guard
against data leakage an unauthorized access. In EDC-V we do not deal with human users, instead, the term "user" is
typically a client application that accesses EDC-V APIs on behalf of a participant. For the purposes of this document,
the terms "user", "client" and "participant" are used interchangeably.

A participant typically represents an organization or a company that is a member of (= "participates" in) a dataspace,
and hence owns a set of resources (data assets, contracts, policies, etc.) in EDC-V.

Client applications, especially graphical user interfaces, that consume EDC-V's [(Administration) APIs](./admin_apis.md)
may very well have individual human users who interact with them, but this is outside the scope of this document.

It is important to note that every identity within EDC-V is associated with a participant context, which is a direct
link to the dataspace participant. That means that a client ID of an API client, for example, is always linked to a
specific participant.

The following diagram illustrates the security boundaries in EDC-V:

![Security Boundaries in EDC-V](./assets/security_boundaries_overview.svg)

All security aspects such as roles, scopes, access privileges etc. are tied to the `participantContextId`.

## API Security

EDC-V's APIs are protected using OAuth2 and the Client Credentials flow. For that, EDC-V relies on a third-party
identity provider (IdP) that supports OAuth2 and JWT tokens. While EDC-V does not mandate a specific IdP, all examples
in this document use Keycloak, which is a very popular open-source IdP.

For API access, clients must obtain an access token from the IdP that satisfies the requirements described in the
[access control document](access_control.md). All client-facing APIs use the same authentication mechanism, which means
that the same token could be re-used for multiple API calls, as long as it is valid (not expired) and contains the
required scopes.

With that, each participant context can access its own resources only, and cannot access resources of other
participants.

## Vault Access

Many API requests involve accessing secure information, which is stored in a secrets management system, a "vault".
Naturally, access to vault secrets must also be restricted to the participant context that owns the secret. For this,
another identity (bound to the participant context) is used to access the vault.

While EDC-V does not mandate a specific vault solution, EDC-V ships with a module for Hashicorp Vault and so all the
examples in this document are based on it. Hashicorp Vault supports several authentication mechanisms including
[AppRole](https://developer.hashicorp.com/vault/docs/auth/approle),
[UserPass](https://developer.hashicorp.com/vault/docs/auth/userpass) and
[JWT](https://developer.hashicorp.com/vault/docs/auth/jwt) in addition to [token-based
authentication](https://developer.hashicorp.com/vault/docs/concepts/tokens).

Generally, all authentication methods ultimately produce a Vault token that is then used to access the vault's secrets.
Those tokens have a finite lifetime and must be renewed before they expire. Once they are expired, they cannot be
renewed.

This poses a problem for EDC-V, because certain operations may be long-running, or may even be executed asynchronously,
for example Credential Offers sent from the IssuerService to IdentityHub would trigger a vault access.

That, and the fact that EDC-V already uses JWT tokens for API access, makes the **JWT authentication method** the
preferred choice for vault access. However, the client-ID for which such JWT tokens are issued, may be different from
the client-ID used for API access. In fact, it is **recommended** to use different client-IDs for API access and vault
access, even though they are both linked to the same participant context. The reasons for this are:

- limited attack surface: if the API client secret is compromised, the vault access remains secure
- principle of least privilege: holders of the API client secret should not be able to access the vault with that secret
- different token shapes: the claims (roles, scopes,...) required for API access may be different from those required
  for vault access
- possibly different IdPs: while it is common to use the same IdP for both API and vault access, there may be scenarios
  where different IdPs are used.

### Secret isolation in Hashicorp Vault

To ensure that each participant context can access its own secrets only, the vault must be configured to isolate
participants from one another. In the open-source version of Hashicorp Vault, this can be achieved by separating each
participant's secrets into individual folders within the secrets engine (=mount) and by enforcing access with ACLs.

It is also possible to put each participant's secrets in a separate mount, but this increases the operational overhead
(backup, management, replication,...) while not providing significant security benefits.

ACL policies must be created for each participant context that restrict access to the respective folder (or mount). For
example, assume the secrets engine is mounted at `participants/secrets`, then an ACL policy would look like this:

```hcl
path "participants/secrets/data/{{identity.entity.aliases.${ACCESSOR}.name}}/*" {
    capabilities = ["create", "read", "update", "delete", "list"]
}

path "participants/secrets/metadata/{{identity.entity.aliases.${ACCESSOR}.name}}/*" {
    capabilities = ["list"]
}
```

An "accessor" is a unique identifier for the JWT authentication method, which is created when enabling the method. In
short, this ACL gives full access to a participant's subfolder only. For example participant context `participant-123`
would only be able to access secrets in the folder `participants/secrets/participant-123/` (the `data` prefix is added
by Vault internally, and is required when accesing the vault via REST API ).

### Configuring Hashicorp Vault's JWT authentication method

The ACL shown above uses the JWT authentication method's built-in variable `${identity.entity.aliases.${ACCESSOR}.name}`
to extract the participant context ID from the JWT token presented during authentication. To make this work, two things
are needed:

- a claim in the JWT token that contains the participant context ID (here: `participant_context_id`)
- a configuration of the JWT authentication method that maps the `participant_context_id` claim onto the entity's "alias
  name".

Please note that any other claim would work as well, as long as it uniquely identifies the participant context ID. Some
IdP's may use the `sub` claim for that. Keycloak does not easily allow to customize that, so we use a custom claim to
contain the participant context ID.

First, we enable JWT authentication and obtain the accessor:

```shell
vault auth enable jwt
ACCESSOR=$(vault auth list | grep 'jwt/' | awk '{print $3}')
```

then the secrets engine is created at the desired path:

```shell
vault secrets enable -path=participants/secrets -version=2 kv
```

Next, the ACL shown above is created in a file `participant-policy.hcl`, and then applied to the vault:

```shell
vault policy write participant-policy participant-policy.hcl
```

The last step is to configure a JWT role:

```shell
 vault write auth/jwt/role/participant -<<EOF
{
    "role_type": "jwt",
    "user_claim": "participant_context_id",
    "bound_issuer": "http://auth.yourdomain.com/realms/your-realm",
    "token_policies": ["participant-policy"],
    "bound_claims": {
        "custom-role": "vault-access"
    },
    "clock_skew_leeway": 60
}
EOF
```

This maps the `participant_context_id` claim onto the entity's alias name, which is then used in the ACL policy to restrict
access to the participant's folder. The `bound_issuer` must match the `iss` claim in the JWT token issued by the IdP.

Vault uses [bound claims](https://developer.hashicorp.com/vault/docs/auth/jwt?utm_source=chatgpt.com#bound-claims) to
further restrict token validation and requires at least one "bound claim" be specified; here we use a custom claim
`custom-role` that must contain the value `vault-access`. This is an additional security measure to ensure that only
tokens intended for vault access are accepted. It is also possible to use named bound claims `bound_audiences` or
`bound_subject`.

In essence, this makes Vault reject tokens, where the `custom-role` claim is missing or does not contain the value
`vault-access` exactly. Note that for this, some additional configuration of the IdP may be required.

_Note: In Hashicorp Vault Enterprise, namespaces can be used to achieve even stronger isolation between participants._

### Storing vault access credentials

Vault access credentials (client ID and client secret) cannot be stored in the vault itself, as that would create a
circular dependency. Instead, those credentials must be stored securely outside of the vault. In EDC-V, all
participant-related configuration is stored in a database, so the (encrypted) vault access credentials are stored there
as well.

## Database Access

Database access is not directly tied to a participant context, as EDC-V uses a shared database for all participants. Instead,
EDC-V's data model ensures that all resources are linked to a specific participant context, and all database queries are
filtered accordingly.

## Protocol Security

EDC-V uses several standardized communication protocols ([Decentralized Claims
Protocol](https://eclipse-dataspace-dcp.github.io/decentralized-claims-protocol/), [Dataspace
Protocol](https://eclipse-dataspace-protocol-base.github.io/DataspaceProtocol/2025-1/) and [Dataplane
Signaling](https://github.com/eclipse-dataplane-signaling)). These protocols include their own authentication and
authorization mechanisms to ensure proper security boundaries between participants. For more information, please refer to the
respective protocol documentation.
