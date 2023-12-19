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

import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;

/**
 * Allows specifying Spring {@link Resource}s as literals referencing AWS Secrets Manager
 * entries. For example, {@code aws-secrets-manager:private-key} will result in a resource
 * that will read from AWS Secrets Manager entry {@code private-key} when an
 * {@link InputStream} is requested (if {@code private-key} doesn't exist or is otherwise
 * unavailable, an {@link IOException} is thrown).
 * <p>
 * Requires a {@code @Bean} of type {@link AWSSecretsManager} to be defined (optionally with
 * {@code @Qualifier("AwsSecretsManagerProtocolResolver")}).
 */
@AutoConfiguration
@ConditionalOnClass(AWSSecretsManager.class)
public class AwsSecretsManagerProtocolBeans {

    @Bean
    ProtocolResolverRegistrar awsSecretsManagerProtocolResolverRegistrar(AWSSecretsManager secretsManager) {
        return new ProtocolResolverRegistrar(new AwsSecretsManagerProtocolResolver(secretsManager));
    }

    static class AwsSecretsManagerProtocolResolver implements ProtocolResolver {
        public static final String PROTOCOL_PREFIX = "aws-secrets-manager:";
        private final AWSSecretsManager secretsManager;

        public AwsSecretsManagerProtocolResolver(@Qualifier("AwsSecretsManagerProtocolResolver") AWSSecretsManager secretsManager) {
            Assert.notNull(secretsManager, "environment cannot be null");
            this.secretsManager = secretsManager;
        }

        @Override
        public Resource resolve(String location, ResourceLoader resourceLoader) {
            if (!location.startsWith(PROTOCOL_PREFIX)) {
                return null;
            }
            String secretName = location.substring(PROTOCOL_PREFIX.length());
            return new AwsSecretsManagerResource(secretsManager, secretName);
        }
    }

    static class AwsSecretsManagerResource extends AbstractNamedResource {
        private final Logger log = LoggerFactory.getLogger(AwsSecretsManagerResource.class);
        private final AWSSecretsManager secretsManager;

        public AwsSecretsManagerResource(AWSSecretsManager secretsManager, String secretId) {
            super(secretId);
            this.secretsManager = secretsManager;
        }

        @Override
        String getValue(String secretId) {
            GetSecretValueRequest request = new GetSecretValueRequest().withSecretId(secretId);
            try {
                GetSecretValueResult result = secretsManager.getSecretValue(request);
                log.info("Retrieved secret arn={}, versionId={}, createdDate={}", result.getARN(), result.getVersionId(), result.getCreatedDate());
                return result.getSecretString();
            } catch (Exception e) {
                log.error("Failed to retrieve secret with secretId=" + secretId, e);
                throw e;
            }
        }
    }
}
