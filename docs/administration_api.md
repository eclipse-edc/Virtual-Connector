# EDC-V Administration APIs

EDC-V is not one single application, instead it consists of multiple services and subsystems. Each of these subsystems
typically comes with a set of APIs, some of which are intended to be internet-facing, some are for internal use only.

Each component of EDC-V offers a way to manipulate its data and configuration via REST APIs. For example, when a new
dataspace participant (sometimes loosely termed a _tenant_) onboards into a dataspace, a few things need to happen: an
(asymmetric) keypair needs to be generated, a DID document needs to be created, and a VerifiableCredential needs to be
requested from the IssuerService. These steps are typically performed by an automated system, a so-called _provisioning
agent_ or just _provisioner_. This may be a shell script, a CI/CD pipeline, or a dedicated application.

In addition to those steps, the newly onboarded participant may want to seed some data of their own, such as managing
their data catalog or requesting more Verifiable Credentials. Naturally, tenant boundaries must be strictly enforced to
avoid data leakage or other security issues.

From that we can see that some API endpoints are intended for _participants_ (~ tenants); others are intended for
automated systems with elevated access rights, such as provisioners.

It is important to note that the Administration APIs are intended for machine clients rather than human actors. This
means that any interactive authentication flows are not supported. All Administration API endpoints assume some machine
client, like a client application or a script.

Please also note that all protocol APIs ([Decentralized Claims
Protocol](https://eclipse-dataspace-dcp.github.io/decentralized-claims-protocol/), [Dataspace
Protocol](https://eclipse-dataspace-protocol-base.github.io/DataspaceProtocol/2025-1/)) are out-of-scope for this
document.

## EDC-V Component Overview

The following diagram shows an overview of the main EDC-V components relevant to the Administration APIs. It does _not_
show optional components, such as the Federated Catalog or external Identity Providers. It also does not show
infrastructure components, such as databases, message brokers, secret vaults, or monitoring systems.

![EDC-V Component Overview](./assets/components.svg)

## Administration API overview

The following table summarizes the Administration APIs offered by EDC-V.

| Name                  | exposed by              | purpose                                                                                | content type | authentication | intended client         |
| --------------------- | ----------------------- | -------------------------------------------------------------------------------------- | ------------ | -------------- | ----------------------- |
| Management API        | ControlPlane            | manage assets, policies, contracts etc.                                                | JSON-LD      | OAuth2         | tenant, provisioner     |
| Identity API          | IdentityHub             | manage VerifiableCredentials, key pairs, DID documents, participants                   | JSON         | OAuth2         | tenant, provisioner     |
| Issuer Admin API      | IssuerService           | manage holders, attestations, credential definitions. Manage individual issuer tenants | JSON-LD      | OAuth2         | provisioner             |
| Signaling API         | ControlPlane, DataPlane | communication between ControlPlane and DataPlane                                       | JSON-LD      | OAuth2         | ControlPlane, DataPlane |
| Observability API     | every component         | observe system readiness and health                                                    | JSON         | none           | monitoring systems      |
| Federated CAtalog API | ControlPlane\*          | query and inspect the consolidated catalog of all data offerings in the system.        | JSON         | OAuth2         | tenant                  |

\*) This API is optional and may not be present in all deployments.

## Roles in EDC-V

A deployment of EDC-V typically involves multiple roles. These are logical roles, and are not necessarily tied to a
single user, or an individual person. However, some roles may be reflected in the identity provider used for
[authentcation and authorization](#authentication-and-authorization).

### Operator

The Operator role is intended for setting up and configuring infrastructure, such as the initial deployment of EDC-V
components. In practice, this role could be represented as ClusterRole in the RBAC Authorization scheme of a Kubernetes
cluster. The operator would then deploy a ControlPlane, IdentityHub, etc. on a Kubernetes cluster, and configure
networking, storage, and other infrastructure components. Over the lifetime of an EDC-V deployment, the Operator might
adjust and reconfigure some parameters, set scaling options etc.

_For Administration APIs the `operator` role is not relevant and is not represented there._

### Admin

The Admin role is similar to a `root` user in Linux. This user has full access to all APIs and all data of all
participants in the system, it may even have access to infrastructure such as Kubernetes or cloud provider consoles. It
should be noted that using this role for day-to-day operations is discouraged, as it increases the risk of accidental
misconfiguration or data leakage or loss!

The `admin` role is intended for initial setup and emergency use only.

_For Administration APIs the `admin` role is identified by having the `role=admin` claim in the OAuth2 token._

### Provisioner

The Provisioner role is tasked with creating and managing dataspace participants (tenants). This includes creating an
entry in the identity provider's user database, creating participant context entries in the IdentityHub and the
ControlPlane as well as creating a Holder entry in the IssuerService.

Provisioners may **not** manipulate data owned by a participant, such as assets, policies, or credentials.

Note that some EDC-V deployments may require additional setup, such as entries in the IssuerService database to feed the
attestation source. This is highly use-case specific and may even require custom APIs that are not shipped with EDC-V,
but those should be used under the Provisioner role. For more information about that, please refer to the [documentation
of the credential issuance
process](https://github.com/eclipse-edc/IdentityHub/blob/main/docs/developer/architecture/issuer/issuance/issuance.process.md).

_For Administration APIs the `provisioner` role is identified by having the `role=tenant-mgr` claim in the OAuth2 token.
In addition, the `provisioner` role requires write access in the identity provider to create new clients._

### Participant

The Participant role represents a single dataspace participant, sometimes loosely termed a tenant. Each participant is
able to manage their own data, such as assets, policies, contracts, and Verifiable Credentials. Each Administration API
that relates to a single participant is available under hosted under the `.../participants/{participantId}/...` path.

Participants have access to _all_ Administration APIs that relate to their tenant, but may **not** access any APIs
relating to other participants.

A company that has onboarded onto a dataspace would typically be represented by a single participant. Naturally, such a
company might mave more than one employee that needs to access the Administration APIs. In most cases, this would be
handled in the identity provider, by creating service identities (for the participant) and user identities (for each
employee). When an employee needs to access the Administration APIs, they would request an access token for the service
identity, for example using the Authorization Code flow of OAuth2 / OpenID Connect. More about this can be found in the
[chapter about end-user UI](#client-ui).

_For Administration APIs the `participant` role is identified by having the `role=participant` claim in the OAuth2
token._

## Authentication and Authorization

The Administration APIs of EDC-V are intended as _single pane of glass_ for both dataspace participants and provisioning
agents. That means the same access token can be used to interact with several APIs (provided it carries the correct
[scopes](#scopes)). To achieve that, all EDC-V components use the same OAuth2-based authentication and authorization
scheme.

### Centralized access control

The single pane of glass approach requires a shared access control scheme between all EDC-V components. On first glance,
this may seem contradictory to EDC's claimed "Decentralized Claims Protocol" and overall "decentralized" nature.
However, this decentralization applies to data exchange between dataspace participants, not to the internal operation of
the EDC-V deployment itself.

EDC-V does not mandate the use of one specific identity provider, instead, it merely requires that the identity provider
supports a [set of requirements](./access_control.md).

Client applications that interact with the Administration APIs must use the _Client Credentials_ grant of OAuth2 to
obtain an access token. Identity providers are **not** part of EDC-V and must be provided by the deployment operator and
they must support OAuth2 Client Credentials grant, and must be able to issue tokens with custom claims. They may also
support the use of refresh tokens, but this is not required as access tokens are typically short-lived.

### Scopes

Access tokens may contain a `scope` claim (as defined by [RFC
9068](https://datatracker.ietf.org/doc/html/rfc9068#section-2.2.3)) that indicates which APIs the token bearer is
allowed to use, and which operations are permitted.

Access tokens that have the `role=admin` claim may omit the `scope` claim, as this role has implicit access to all APIs
and operations.

See the [Access Control documentation](./access_control.md#scopes) for details.

### Custom OAuth2 token claims

To avoid having to maintain a client database with associated roles in every EDC-V component (and keep them in sync),
EDC-V relies on the Identity Provider to issue OAuth2 access tokens with custom claims that contain the client's role.

In addition, the `participant_context_id` claim is used to identify the participant on whose behalf the client is
acting. This is needed to cross-reference the participant with the requested resource, typically identified by a URL
path like `/participants/{participantContextId}/some-resource`.

This is described in detail in the [Access Control documentation](./access_control.md#custom-token-claims).

## Administration API usage overview

The following diagram shows an overview of all involved components, their roles, and which APIs they access.

![API Usage](./assets/roles_and_apis.svg)

## Client UI

The Client UI is used by employes of a company who are members of a dataspace. Architecturally, the Client UI
communicates only with a UI Backend application. This is a common pattern in modern web development. The only component
exposed to the public internet is the UI Backend, and it is subject to scalability and security requirements of the user
base.

The Client UI implements use cases that revolve around a single participant. For example, it would display a list of a
participant context's assets, their ongoing transfers, their contracts, policies etc. It may also implement features to
update DID Documents, request credentials etc.

The Client UI and the UI Backend are **not** included in an EDC-V deployment and must be developed by each organisation
that wants to run a dataspace individually. This UI is likely very specific to each dataspace, therefor the
authentication mechanisms used between Client UI <-> UI Backend are out-of-scope for this document. However, each
physical user must be mapped onto a client with `role=participant` in EDC-V's identity provider.

### Communication paths and roles

When it needs to manipulate tenant/participant data, the UI Backend communicates with the Connector Fabric Manager (CFM,
a software component to create/delete participant contexts), using the CFM's API.

In addition, when a user wants to make changes to their data (assets, policies, etc.) the UI Backend communicates with
Administration APIs of EDC-V, using the `participant` role, again, acting on behalf of the logged-in user.

## Connector Fabric Manager

As part of the onboarding process of a tenant/participant, the Connector Fabric Manager (CFM) communicates with the
Administration APIs of EDC-V using the `tenant-mgr` role. It **never** communicates directly with
participant-context-specific resource APIs (recognizable by their URL path
`/participants/{participantContextId}/some-resource`).

The CFM does not interact on behalf of a participant-context, instead it always acts as `role=tenant-mgr`, interacting
with those parts of the Adminstration API, that are not participant-context-specific.

## Operations UI

The Operations UI (or ops UI) is intended for dataspace operators, who need to manage the infrastructure, where EDC-V is
deployed. In many cases this will mean deploying, linking and managing multiple Kubernetes clusters ("cells"),
dynamically scaling individual component workloads, provision additional compute resources etc.

The ops UI has no contact points with the Administration APIs of EDC-V. The authentication scheme between the Operations
UI and its backend is out-of-scope for this document.
