package com.commercex.auth.application;

import com.commercex.auth.domain.RefreshToken;
import com.commercex.auth.domain.User;
import com.commercex.auth.infrastructure.RefreshTokenRepository;
import com.commercex.common.security.JwtClaims;
import com.commercex.common.security.SecureTokens;
import com.commercex.common.security.SecurityProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** Issues the access JWT plus a refresh token in the given (or a new) rotation family. */
@Component
class TokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final RefreshTokenRepository refreshTokens;
    private final SecurityProperties securityProperties;
    private final AuthProperties authProperties;
    private final Clock clock;

    TokenIssuer(JwtEncoder jwtEncoder, RefreshTokenRepository refreshTokens, SecurityProperties securityProperties,
            AuthProperties authProperties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.refreshTokens = refreshTokens;
        this.securityProperties = securityProperties;
        this.authProperties = authProperties;
        this.clock = clock;
    }

    AuthResult issueNewSession(User user) {
        return issue(user, UUID.randomUUID()).result();
    }

    Issued issue(User user, UUID familyId) {
        Instant now = clock.instant();
        Instant accessExpiresAt = now.plus(securityProperties.jwt().accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(securityProperties.jwt().issuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(accessExpiresAt)
                .id(UUID.randomUUID().toString())
                .claim(JwtClaims.ROLES, user.getRoles().stream().map(Enum::name).sorted().toList())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        String rawRefresh = SecureTokens.generate();
        Instant refreshExpiresAt = now.plus(authProperties.refreshTokenTtl());
        RefreshToken refresh = refreshTokens.save(
                RefreshToken.issue(user.getId(), SecureTokens.sha256(rawRefresh), familyId, now, refreshExpiresAt));

        AuthResult result = new AuthResult(accessToken, accessExpiresAt,
                securityProperties.jwt().accessTokenTtl().toSeconds(), rawRefresh, refreshExpiresAt,
                UserView.from(user));
        return new Issued(result, refresh);
    }

    record Issued(AuthResult result, RefreshToken refreshToken) {
    }
}
