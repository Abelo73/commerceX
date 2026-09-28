package com.commercex.common.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * RS256 key material for issuing and validating access tokens.
 * Production must provide PEM keys; dev/test may opt into an ephemeral key pair.
 */
@Configuration(proxyBeanMethods = false)
class JwtKeyConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyConfig.class);

    @Bean
    RSAKey jwtSigningKey(SecurityProperties properties) {
        SecurityProperties.Jwt jwt = properties.jwt();
        RSAPublicKey publicKey;
        RSAPrivateKey privateKey;
        if (jwt.privateKeyLocation() != null && jwt.publicKeyLocation() != null) {
            privateKey = read(jwt.privateKeyLocation(), true);
            publicKey = read(jwt.publicKeyLocation(), false);
        } else if (jwt.allowEphemeralKeys()) {
            log.warn("Using an ephemeral JWT signing key: tokens will not survive a restart. Never use in production.");
            KeyPair pair = generate();
            publicKey = (RSAPublicKey) pair.getPublic();
            privateKey = (RSAPrivateKey) pair.getPrivate();
        } else {
            throw new IllegalStateException(
                    "commercex.security.jwt.private-key-location and public-key-location must be configured");
        }
        try {
            return new RSAKey.Builder(publicKey).privateKey(privateKey).keyIDFromThumbprint().build();
        } catch (JOSEException e) {
            throw new IllegalStateException("Cannot build JWK", e);
        }
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(jwtSigningKey)));
    }

    @Bean
    JwtDecoder jwtDecoder(RSAKey jwtSigningKey, SecurityProperties properties) throws JOSEException {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(jwtSigningKey.toRSAPublicKey()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.jwt().issuer())));
        return decoder;
    }

    @SuppressWarnings("unchecked")
    private static <T> T read(Resource resource, boolean isPrivate) {
        try (InputStream in = resource.getInputStream()) {
            return (T) (isPrivate ? RsaKeyConverters.pkcs8().convert(in) : RsaKeyConverters.x509().convert(in));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read JWT key from " + resource, e);
        }
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
