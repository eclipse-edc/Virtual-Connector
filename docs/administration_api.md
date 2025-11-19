# EDC-V Administration APIs

EDC-V is not a monolith. It consists of multiple services and subsystems. Each of these subsystems typically comes with
a set of APIs, some of which are intended to be internet-facing, others are for internal use only. In this document we
will focus on the earlier class of APIs, which are typically for _internal use only_.

Each component of EDC-V offers ways to manipulate its data and configuration via REST APIs. For example, when a new
dataspace participant onboards into a dataspace, a few things need to happen: an (asymmetric) keypair needs to be
generated, a DID document needs to be created, and a VerifiableCredential needs to be requested from the IssuerService.
These steps are typically performed by an automated system, a so-called _provisioning system_. This may be a shell
script, a CI/CD pipeline, or a dedicated management plane.

To do that, the provisioning system must communicate with several APIs, creating resources.

In addition to those steps, the newly onboarded participant may want to manage some data of their own, such as data
sharing or request more Verifiable Credentials or initiate the download of another data offering. Naturally, isolation
boundaries must be strictly enforced between participants to avoid data leakage or other security issues.

From that we can see that some API endpoints are intended for _participants_ ; others are intended for automated systems
with elevated access rights, such as provisioners. From that it follows that two types of authorization privileges/roles
are required: that of a participant and that of a provisioner. Roles are explained in detail [in this
chapter](#roles-in-edc-v)

It is important to note that all Administration APIs are intended for machine clients rather than human actors. Hence,
only authentication flows are supported that don't require human interaction. All Administration API endpoints assume
some machine client, like a client application or a script.

Please also note that all protocol APIs ([Decentralized Claims
Protocol](https://eclipse-dataspace-dcp.github.io/decentralized-claims-protocol/), [Dataspace
Protocol](https://eclipse-dataspace-protocol-base.github.io/DataspaceProtocol/2025-1/) and [Dataplane
Signaling](https://github.com/eclipse-dataplane-signaling)) are out-of-scope for this document.

## EDC-V Component Overview

The following diagram shows an overview of the main EDC-V components relevant to the Administration APIs. It does _not_
show optional components, such as the Federated Catalog or external Identity Providers. It also does not show
infrastructure components, such as databases, message brokers, secret vaults, or monitoring systems.

![EDC-V Component Overview](./assets/components.svg)

## Administration API overview

The following table summarizes the Administration APIs offered by EDC-V.

| Name                  | exposed by      | purpose                                                                                | content type | authentication | intended client     |
|-----------------------|-----------------|----------------------------------------------------------------------------------------|--------------|----------------|---------------------|
| Management API        | ControlPlane    | manage assets, policies, contracts etc.                                                | JSON-LD      | OAuth2         | tenant, provisioner |
| Identity API          | IdentityHub     | manage VerifiableCredentials, key pairs, DID documents, participants                   | JSON         | OAuth2         | tenant, provisioner |
| Issuer Admin API      | IssuerService   | manage holders, attestations, credential definitions. Manage individual issuer tenants | JSON-LD      | OAuth2         | provisioner         |
| Observability API     | every component | observe system readiness and health                                                    | JSON         | none           | monitoring systems  |
| Federated Catalog API | ControlPlane\*  | query and inspect the consolidated catalog of all data offerings in the system.        | JSON         | OAuth2         | tenant              |

\*) This API is optional and may not be present in all deployments.

## Roles in EDC-V

A deployment of EDC-V typically involves multiple roles. These are logical roles, and are not necessarily tied to a
single user, or an individual person. However, some roles may be reflected in the identity provider used for
[authentcation and authorization](#authentication-and-authorization).

### Operator

The Operator role is intended for setting up and configuring infrastructure, such as the initial deployment of EDC-V
components. In practice, this role could be represented as ClusterRole in the RBAC Authorization scheme of a Kubernetes
cluster. The operator would then configure the cluster, deploy a ControlPlane, IdentityHub, etc., and configure
networking (DNS), storage, and other infrastructure components. Over the lifetime of an EDC-V deployment, the Operator
might adjust and reconfigure some parameters, set scaling options etc. Operators might even have access to third-party
systems such as DNS providers.

_For Administration APIs the `operator` role is not relevant and is not represented._

### Admin

The Admin role is similar to a `root` user in Linux. This user has full access to all APIs and all data of all
participants in the system, it may even have access to infrastructure such as Kubernetes or cloud provider consoles. It
should be noted that using this role for day-to-day operations is discouraged, as it increases the risk of accidental
misconfiguration or data leakage or loss!

The `admin` role is intended for initial setup and emergency use only.

_For Administration APIs the `admin` role is identified by having the `role=admin` claim in the OAuth2 token._

### Provisioning System (or _provisioner_)

The Provisioning System is tasked with creating and managing dataspace participants. This includes creating an entry in
the identity provider's user database, creating participant context entries in the IdentityHub and the ControlPlane as
well as creating a Holder entry in the IssuerService.

Provisioning Systems may **not** manipulate data owned by a participant, such as assets, policies, or credentials.

Note that some EDC-V deployments may require additional setup, such as entries in the IssuerService database to feed the
attestation source. This is highly use-case specific and may even require custom APIs that are not shipped with EDC-V,
but those should be used under the Provisioning System role. For more information about that, please refer to the
[documentation of the credential issuance
process](https://github.com/eclipse-edc/IdentityHub/blob/main/docs/developer/architecture/issuer/issuance/issuance.process.md).

_For Administration APIs the Provisioning System role is identified by having the `role=provisioner` claim in the OAuth2
token. In addition, the provisioning system role requires write access in the identity provider to create new clients._

### Participant

The Participant role represents a single dataspace participant. Each participant is able to manage their own data, such
as assets, policies, contracts, and Verifiable Credentials. Each Administration API that relates to a single participant
is available under hosted under the `.../participants/{participantId}/...` path.

Participants have access to _all_ Administration APIs that relate to their tenant, but may **not** access any APIs
relating to other participants or generic Administration APIs, e.g., to create other participants.

A company that has onboarded onto a dataspace would typically be represented by a single participant. Naturally, such a
company might mave more than one employee that needs to access the Administration APIs. In most cases, this would have
to be handled by the user interface (the "End-User UI") and its identity provider, by creating user identities for each
employee and mapping those onto the service identity. When an employee needs to access the Administration APIs, they
would request an access token for the service identity, for example using the Authorization Code flow of OAuth2 / OpenID
Connect. More about this can be found in the [chapter about end-user UI](#end-user-ui).

_For Administration APIs the `participant` role is identified by having the `role=participant` claim in the OAuth2
token._

## Authentication and Authorization

The Administration APIs of EDC-V are intended as _single pane of glass_ for both dataspace participants and
provisioners. That means the same access token can be used to interact with several APIs (provided it carries the
correct [scopes](#scopes)). To achieve that, all EDC-V components use the same OAuth2-based authentication and
authorization scheme.

### Centralized access control

The single pane of glass approach requires a shared access control scheme between all EDC-V components. On first glance,
this may seem contradictory to EDC's claimed "Decentralized Claims Protocol" and overall "decentralized" nature, but
this decentralization applies to data exchange between dataspace participants, not to the internal operation of the
EDC-V deployment itself.

EDC-V does not mandate the use of one specific identity provider and does not distribute one. Rather, it requires the
identity provider to support a [set of requirements](./access_control.md).

Client applications that interact with the Administration APIs must use the _OAuth 2 Client Credentials_ grant to obtain
an access token. Identity providers are **not** part of EDC-V and must be provided by the deployment operator and they
must support OAuth2 Client Credentials grant, and must be able to issue tokens with custom claims. They may also support
the use of refresh tokens, but this is not required as access tokens are typically short-lived.

### Scopes

Access tokens may contain a `scope` claim (as defined by [RFC
9068](https://datatracker.ietf.org/doc/html/rfc9068#section-2.2.3)) that indicates which APIs the token bearer is
allowed to use, and which operations are permitted.

Access tokens that have the `role=admin` claim need not carry the `scope` claim, as this role has implicit access to all
APIs and operations. Tokens with the `role=participant` claim _MUST_ carry the scope claim.

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

## End-User UI

The End-User UI is used by employees of a company that is a participant in a dataspace. The End-User UI communicates
only with a UI Backend application. This is a common pattern in modern web development. The only component exposed to
the public internet is the UI Backend, and it is subject to scalability and security requirements of the user base.

The End-User UI implements use cases that revolve around a single participant. For example, it would display a list of a
participant's assets, their ongoing transfers, their contracts, policies, etc. It may also implement features to
update DID Documents, request credentials, etc.

The End-User UI and the UI Backend are **not** included in an EDC-V deployment and must be developed by each
organization that wants to run a dataspace individually. This UI and the use cases it models are likely very specific to
each dataspace, therefore, the authentication mechanisms used between End-User UI ↔ UI Backend are out-of-scope for this
document. However, each physical user must be mapped onto a client with `role=participant` in EDC-V's identity provider.

### Communication paths and roles

When it needs to manipulate tenant/participant data, the UI Backend communicates with the provisioning system (a
software component to create/delete participant contexts), using its API.

In addition, when a user wants to make changes to their data (assets, policies, etc.) the UI Backend communicates with
Administration APIs of EDC-V, using the `participant` role, again, acting on behalf of the logged-in user.

## Provisioning System

As part of the onboarding process of a tenant/participant, the Provisioning System communicates with the Administration
APIs of EDC-V using the `provisioner` role. It **never** communicates directly with participant-context-specific
resource APIs (recognizable by their URL path `/participants/{participantContextId}/some-resource`).

The Provisioning System does not interact on behalf of a participant-context; instead it always acts as
`role=provisioner`, interacting with those parts of the Administration API, that are not participant-context-specific.

## Operations UI

The Operations UI (or ops UI) is intended for dataspace operators, who need to manage the infrastructure where EDC-V is
deployed. In many cases this will mean deploying, linking and managing multiple Kubernetes clusters ("cells"),
dynamically scaling individual component workloads, provision additional compute resources, etc.

The Operations UI has no contact points with the Administration APIs of EDC-V. The authentication scheme between the
Operations UI and its backend is out-of-scope for this document.
