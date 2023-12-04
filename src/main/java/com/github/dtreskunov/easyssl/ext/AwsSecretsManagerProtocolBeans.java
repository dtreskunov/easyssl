package com.github.dtreskunov.easyssl.ext;

import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ProtocolResolver;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;

import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

/**
 * Allows specifying Spring {@link Resource}s as literals referencing AWS Secrets Manager
 * entries. For example, {@code aws-secrets-manager:private-key} will result in a resource
 * that will read from AWS Secrets Manager entry {@code private-key} when an
 * {@link InputStream} is requested (if {@code private-key} doesn't exist or is otherwise
 * unavailable, an {@link IOException} is thrown).
 * <p>
 * Requires a {@code @Bean} of type {@link SecretsManagerClient} to be defined (optionally with
 * {@code @Qualifier("AwsSecretsManagerProtocolResolver")}).
 */
@AutoConfiguration
@ConditionalOnClass(SecretsManagerClient.class)
public class AwsSecretsManagerProtocolBeans {

    @Bean
    ProtocolResolverRegistrar awsSecretsManagerProtocolResolverRegistrar(SecretsManagerClient secretsClient) {
        return new ProtocolResolverRegistrar(new AwsSecretsManagerProtocolResolver(secretsClient));
    }

    static class AwsSecretsManagerProtocolResolver implements ProtocolResolver {
        public static final String PROTOCOL_PREFIX = "aws-secrets-manager:";
        private final SecretsManagerClient secretsClient;

        public AwsSecretsManagerProtocolResolver(@Qualifier("AwsSecretsManagerProtocolResolver") SecretsManagerClient secretsClient) {
            Assert.notNull(secretsClient, "environment cannot be null");
            this.secretsClient = secretsClient;
        }

        @Override
        public Resource resolve(String location, ResourceLoader resourceLoader) {
            if (!location.startsWith(PROTOCOL_PREFIX)) {
                return null;
            }
            String secretName = location.substring(PROTOCOL_PREFIX.length());
            return new AwsSecretsManagerResource(secretsClient, secretName);
        }
    }

    static class AwsSecretsManagerResource extends AbstractNamedResource {
        private final Logger log = LoggerFactory.getLogger(AwsSecretsManagerResource.class);
        private final SecretsManagerClient secretsClient;

        public AwsSecretsManagerResource(SecretsManagerClient secretsClient, String secretId) {
            super(secretId);
            this.secretsClient = secretsClient;
        }

        @Override
        String getValue(String secretId) {
            GetSecretValueRequest request = GetSecretValueRequest
                .builder()
                .secretId(secretId)
                .build();
            try {
                GetSecretValueResponse response = secretsClient.getSecretValue(request);
                log.info("Retrieved secret arn={}, versionId={}, createdDate={}", response.arn(), response.versionId(), response.createdDate());
                return response.secretString();
            } catch (Exception e) {
                log.error("Failed to retrieve secret with secretId=" + secretId, e);
                throw e;
            }
        }
    }
}
