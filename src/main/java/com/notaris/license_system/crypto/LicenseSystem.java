package com.notaris.license_system.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

public class LicenseSystem {
    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", "BC");
            kpg.initialize(new ECGenParameterSpec("P-521"), new SecureRandom());
            return kpg.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] generateAesKey() {
        try {
            KeyGenerator kg = KeyGenerator.getInstance("AES");
            kg.init(256);
            SecretKey key = kg.generateKey();
            return key.getEncoded();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void savePrivate(ECPrivateKey key, Path path) throws IOException {
        // PKCS8 DER -> Base64 PEM
        byte[] der = key.getEncoded();
        writePem(der, "PRIVATE KEY", path);
    }

    public static void savePublic(ECPublicKey key, Path path) throws IOException {
        byte[] der = key.getEncoded();
        writePem(der, "PUBLIC KEY", path);
    }

    private static void writePem(byte[] der, String type, Path path) throws IOException {
        String b64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
        String pem = "-----BEGIN " + type + "-----\n" + b64 + "\n-----END " + type + "-----\n";
        Files.writeString(path, pem);
    }

    public static ECPublicKey loadPublic(Path path) {
        try {
            byte[] der = parsePem(Files.readString(path));
            KeyFactory kf = KeyFactory.getInstance("EC");
            return (ECPublicKey) kf.generatePublic(new java.security.spec.X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static ECPrivateKey loadPrivate(Path path) {
        try {
            byte[] der = parsePem(Files.readString(path));
            KeyFactory kf = KeyFactory.getInstance("EC");
            return (ECPrivateKey) kf.generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] loadAes(Path path) throws IOException {
        return Files.readAllBytes(path);
    }

    private static byte[] parsePem(String pem) {
        String body = pem.replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }
}