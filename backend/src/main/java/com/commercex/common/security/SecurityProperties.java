package com.commercex.common.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/**
 * @param jwt  access-token signing configuration
 * @param cors allowed browser origins (credentials are allowed, so never use "*")
 */
@ConfigurationProperties("commercex.security")
public record SecurityProperties(Jwt jwt, Cors cors) {

    /**
     * @param issuer              {@code iss} claim, validated on every request
     * @param accessTokenTtl      access token lifetime
     * @param privateKeyLocation  PKCS#8 PEM; required unless {@code allowEphemeralKeys}
     * @param publicKeyLocation   X.509 PEM; required unless {@code allowEphemeralKeys}
     * @param allowEphemeralKeys  dev/test only: generate a throwaway key pair at startup
     */
    public record Jwt(
            String issuer,
            Duration accessTokenTtl,
            Resource privateKeyLocation,
            Resource publicKeyLocation,
            boolean allowEphemeralKeys) {
    }

    public record Cors(List<String> allowedOrigins) {
    }
}
