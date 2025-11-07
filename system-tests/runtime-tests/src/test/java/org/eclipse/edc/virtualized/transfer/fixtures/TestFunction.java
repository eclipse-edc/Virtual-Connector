/*
 *  Copyright (c) 2025 Cofinity-X
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Cofinity-X - initial API and implementation
 *
 */

package org.eclipse.edc.virtualized.transfer.fixtures;

import org.eclipse.edc.iam.verifiablecredentials.spi.RevocationListService;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialStatus;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.Issuer;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.RevocationServiceRegistry;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.iam.verifiablecredentials.spi.validation.TrustedIssuerRegistry;
import org.eclipse.edc.identityhub.spi.participantcontext.model.CreateParticipantContextResponse;
import org.eclipse.edc.identityhub.tests.fixtures.credentialservice.IdentityHub;
import org.eclipse.edc.identityhub.tests.fixtures.issuerservice.IssuerService;
import org.eclipse.edc.issuerservice.spi.issuance.model.AttestationDefinition;
import org.eclipse.edc.issuerservice.spi.issuance.model.CredentialDefinition;
import org.eclipse.edc.issuerservice.spi.issuance.model.CredentialRuleDefinition;
import org.eclipse.edc.issuerservice.spi.issuance.model.MappingDefinition;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.Map;

import static org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialFormat.VC1_0_JWT;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;

public class TestFunction {


    public static void setupIssuer(IssuerService issuerService, String participantContextId, String did) {
        issuerService.createParticipant(participantContextId, did, did + "#key");

        var attestationDefinition = AttestationDefinition.Builder.newInstance().id("attestation-id")
                .attestationType("database")
                .participantContextId(participantContextId)
                .configuration(Map.of(
                        "dataSourceName", "default",
                        "tableName", "attestations",
                        "idColumn", "holderId"))
                .build();

        issuerService.createAttestationDefinition(attestationDefinition);

        Map<String, Object> ruleConfiguration = Map.of(
                "claim", "member_signed_document",
                "operator", "eq",
                "value", "t");

        var credentialDefinition = CredentialDefinition.Builder.newInstance()
                .id("credential-id")
                .credentialType("MembershipCredential")
                .jsonSchemaUrl("https://example.com/schema")
                .jsonSchema("{}")
                .attestation(attestationDefinition.getId())
                .validity(3600)
                .mapping(new MappingDefinition("member_name", "credentialSubject.name", true))
                .mapping(new MappingDefinition("membership_start_date", "credentialSubject.membershipStartDate", true))
                .rule(new CredentialRuleDefinition("expression", ruleConfiguration))
                .participantContextId(participantContextId)
                .formatFrom(VC1_0_JWT)
                .build();

        issuerService.createCredentialDefinition(credentialDefinition);

        var dataSourceRegistry = issuerService.getService(DataSourceRegistry.class);
        var tx = issuerService.getService(TransactionContext.class);
        var executor = issuerService.getService(QueryExecutor.class);
        var dataSource = dataSourceRegistry.resolve("default");

        tx.execute(() -> {
            try (var connection = dataSource.getConnection()) {
                executor.execute(connection, "CREATE TABLE attestations (holderId VARCHAR(255), member_name VARCHAR(255), membership_start_date timestamp, member_signed_document BOOLEAN)");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });

    }

    public static void setupHolder(IssuerService issuerService, String participantContextId, String holderId) {

        issuerService.createHolder(participantContextId, holderId, holderId, holderId);


        var dataSourceRegistry = issuerService.getService(DataSourceRegistry.class);
        var tx = issuerService.getService(TransactionContext.class);
        var executor = issuerService.getService(QueryExecutor.class);

        tx.execute(() -> {
            var dataSource = dataSourceRegistry.resolve("default");

            try (var connection = dataSource.getConnection()) {
                executor.execute(connection, "INSERT INTO attestations (holderId, member_name, membership_start_date, member_signed_document) VALUES (?, ?, now(), true)", holderId, holderId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static CreateParticipantContextResponse setupParticipant(IdentityHub identityHub, VirtualConnector connector, String issuerDid, String holderId) {

        var response = identityHub.createParticipant(holderId, holderId, holderId + "#key");

        var vault = connector.getService(Vault.class);
        vault.storeSecret(holderId + "-alias", response.clientSecret());

        var revocationRegistry = connector.getService(RevocationServiceRegistry.class);

        revocationRegistry.addService("BitstringStatusListEntry", new RevocationListService() {
            @Override
            public Result<Void> checkValidity(CredentialStatus credentialStatus) {
                return Result.success();
            }

            @Override
            public Result<String> getStatusPurpose(VerifiableCredential verifiableCredential) {
                return null;
            }
        });

        connector.getService(TrustedIssuerRegistry.class).register(new Issuer(issuerDid, Map.of()), "*");


        return response;
    }

    public static @NotNull Map<String, Object> httpSourceDataAddress() {
        return Map.of(
                EDC_NAMESPACE + "name", "transfer-test",
                EDC_NAMESPACE + "baseUrl", "http://anysource.com",
                EDC_NAMESPACE + "type", "HttpData",
                EDC_NAMESPACE + "proxyQueryParams", "true"
        );
    }
}
