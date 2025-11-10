# Dynamic policy evaluation in EDC-V with Common Expression Language (CEL)

This document provides an experimental work for enabling EDC-V to evaluate policies using the Common
Expression Language (CEL), without requiring users to write custom Java code for policy functions.
CEL is a powerful, non-Turing complete expression language designed for simplicity, safety,
and performance. For more information about CEL, visit the [CEL](https://cel.dev/) website.

## Overview

EDC-V currently supports policy evaluation through Java-based policy functions. While this approach is flexible,
it requires users to write and maintain Java code for each custom policy function, it also requires
a redeployment of the EDC-V runtime to apply changes when new requirements arise.
By integrating CEL into EDC-V, we aim to provide a more user-friendly way to define and evaluate policies dynamically.

## Integration of CEL into EDC-V

To integrate CEL into EDC-V, we'd have to implement a custom policy function that leverages the CEL Java library for
evaluating expressions defined in CEL syntax. This policy function will be capable of interpreting CEL expressions
and evaluating them against the context of the policy evaluation.

Each CEL expression must be associated with a specific atomic constraint identified by the `leftOperand` field.
Within the CEL expression, some variables are predefined to represent the context of the policy evaluation:

- `this`: Represents the current atomic constraint being evaluated (including its `leftOperand`, `operator`, and
  `rightOperand`).
- `ctx`: Represents the evaluation context, which may include additional information relevant to the policy
  evaluation like attributes of the requesting party (participant agent), the contract negotiation (in the transfer
  scope)
- `now` : Represents the current timestamp in milliseconds since the epoch.

When populating the `ctx` variable, EDC-V should include relevant attributes from the participant agent and contract
negotiation in form of a map, allowing CEL expressions to access these attributes during evaluation.

## Example CEL Expressions within EDC-V

Here are some example CEL expressions that demonstrate how to use the predefined variables:

Simple equality check:

```cel
this.rightOperand == "allowedValue"
```

Check if the current time is before a specified timestamp in the `rightOperand`:

```cel
now < timestamp(this.rightOperand) 
```

Check if a participant agent id matches the `rightOperand`:

```cel
ctx.agent.id == this.rightOperand
```

Check if a participant agent has a `MembershipCredential` credentials:

```cel
ctx.agent.claims.vc
.exists(c, c.type.exists(t, t == 'MembershipCredential'))
```

## Participant Agent properties in CEL

When evaluating CEL expressions, the `ctx.agent` variable provides access to the properties of the `ParticipantAgent`
class in EDC.

The following properties will be available:

- `ctx.agent.id`: The unique identifier of the participant agent.
- `ctx.agent.claims`: The claims associated with the participant agent.
- `ctx.agent.attributes`: A map of additional attributes associated with the participant agent.

The `claims` and `attributes` content depends on the `IdentityService` implementation used in EDC-V.

For example, when using `dcp` as `IdentityService`, the `ctx.agent.claims` will include verifiable credentials
(fetched during a `dcp` presentation flow), which can be accessed and evaluated within CEL
expressions as shown in the previous examples.

## Implementation Details

The integration of CEL into EDC-V involves the following key components:

We will introduce a new component for evaluating CEL expressions within the policy evaluation framework of EDC-V:

```java
public interface CelExpressionEngine {

    boolean canEvaluate(String leftOperand);

    ServiceResult<Void> validate(String expression);

    boolean evaluateExpression(Object leftOperand, Operator operator, Object rightOperand, Map<String, Object> params);
}
```

Which will wrap the CEL Java library to compile and evaluate CEL expressions. The `CelExpressionEngine` interface
defines methods for checking if a
CEL expression can be evaluated, validating the expression syntax, and evaluating the expression with given operands and
context parameters.

The `CelExpressionEngine` should have access to a repository of CEL expressions mapped to their corresponding
`leftOperand` values. At first, we will use a `CelExpressionStore` that retrieves CEL expressions.
For simplicity, we can start with an in-memory store.

Optimization, caching and different storage backends will be considered in the future development.

The `CelExpressionEngine` will be integrated into the existing policy evaluation flow of EDC-V by creating a new
policy function, e.g., `CelExpressionFunction`, which implements the `DynamicAtomicConstraintRuleFunction` interface:

```java
public record CelExpressionFunction<C extends ParticipantAgentPolicyContext>(
        CelExpressionEngine engine) implements DynamicAtomicConstraintRuleFunction<Permission, C> {

    @Override
    public boolean evaluate(Object leftOperand, Operator operator, Object rightOperand, Permission permission, C c) {
        return engine.evaluateExpression(leftOperand.toString(), operator, rightOperand, toParams(c));
    }

    @Override
    public boolean canHandle(Object leftOperand) {
        return engine.canEvaluate(leftOperand.toString());
    }

    // convert context to parameters map for CEL evaluation
    private Map<String, Object> toParams(C context) {

    }
}
```

The `CelExpressionFunction` will be registered within the EDC-V `PolicyEngine`, allowing it to be used for
evaluating policies defined using CEL expressions.

For managing the CEL expressions, we will implement a `CelExpressionStore` and `CelExpressionService` to handle the
storage and retrieval of CEL expressions associated with specific `leftOperand` values.

We will also introduce a new model class, e.g., `CelExpression`, to represent the CEL expressions stored in the
system:

```java
public record CelExpression(String id,
                            String leftOperand,
                            String expression,
                            String description, Long createdAt, Long updatedAt) {
}
```

