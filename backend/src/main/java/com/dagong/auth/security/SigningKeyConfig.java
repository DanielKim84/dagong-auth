package com.dagong.auth.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 토큰 서명 키를 PKCS12 파일에서 읽어 JWKS로 제공합니다. */
@Configuration
public class SigningKeyConfig {
    /**
     * 인증서 지문을 JWK ID로 사용해 재시작 후에도 같은 공개 키 식별자를 제공하고,
     * 기존 토큰 검증을 이어갈 수 있게 합니다.
     */
    @Bean
    JWKSource<SecurityContext> jwkSource(
            @Value("${dagong.auth.keystore-path}") String keystorePath,
            @Value("${dagong.auth.keystore-password}") String keystorePassword,
            @Value("${dagong.auth.keystore-alias}") String keyAlias)
            throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(Path.of(keystorePath))) {
            keyStore.load(input, keystorePassword.toCharArray());
        }

        PrivateKey privateKey = (PrivateKey) keyStore.getKey(keyAlias, keystorePassword.toCharArray());
        X509Certificate certificate = (X509Certificate) keyStore.getCertificate(keyAlias);
        if (privateKey == null || certificate == null) {
            throw new IllegalStateException("PKCS12 키 저장소에 서명 키 별칭이 없습니다: " + keyAlias);
        }

        String keyId = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
        RSAKey key = new RSAKey.Builder((RSAPublicKey) certificate.getPublicKey())
                .privateKey((RSAPrivateKey) privateKey)
                .keyID(keyId)
                .build();
        JWKSet keySet = new JWKSet(key);

        return (selector, context) -> selector.select(keySet);
    }
}
