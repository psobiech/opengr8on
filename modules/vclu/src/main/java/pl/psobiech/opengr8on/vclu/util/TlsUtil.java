/*
 * OpenGr8on, open source extensions to systems based on Grenton devices
 * Copyright (C) 2023 Piotr Sobiech
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package pl.psobiech.opengr8on.vclu.util;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.io.pem.PemReader;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;
import pl.psobiech.opengr8on.util.RandomUtil;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;

public class TlsUtil {
    public static final BouncyCastleProvider BOUNCY_CASTLE_PROVIDER = new BouncyCastleProvider();

    private static final CertificateFactory CERTIFICATE_FACTORY;

    private static final KeyFactory RSA_KEY_FACTORY;

    private static final KeyFactory ECDSA_KEY_FACTORY;

    static {
        // System.setProperty("javax.net.debug", "all");

        try {
            CERTIFICATE_FACTORY = CertificateFactory.getInstance("X.509");

            RSA_KEY_FACTORY = KeyFactory.getInstance("RSA");
            ECDSA_KEY_FACTORY = KeyFactory.getInstance("ECDSA", BOUNCY_CASTLE_PROVIDER);
        } catch (NoSuchAlgorithmException | CertificateException e) {
            throw new UnexpectedException(e);
        }
    }

    private TlsUtil() {
        // NOP
    }

    public static SSLSocketFactory createSocketFactory(Path caCertificatePath, Path clientCertificatePath, Path clientKeyPath) {
        try {
            final KeyStore caKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            caKeyStore.load(null, null);
            caKeyStore.setCertificateEntry("certificate", readCertificate(caCertificatePath));

            final KeyStore clientKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            clientKeyStore.load(null, null);
            if (Files.exists(clientCertificatePath) && Files.exists(clientKeyPath)) {
                final X509Certificate clientCertificate = readCertificate(clientCertificatePath);
                clientKeyStore.setCertificateEntry("certificate", clientCertificate);
                clientKeyStore.setKeyEntry("key", readPrivateKey(clientKeyPath), null, new java.security.cert.Certificate[]{clientCertificate});
            }

            final KeyManagerFactory clientKeyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            clientKeyManagerFactory.init(clientKeyStore, null);

            final TrustManagerFactory caTrustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            caTrustManagerFactory.init(caKeyStore);

            final SSLContext tlsContext = SSLContext.getInstance("TLSv1.2");
            tlsContext.init(clientKeyManagerFactory.getKeyManagers(), caTrustManagerFactory.getTrustManagers(), RandomUtil.RANDOM);

            return tlsContext.getSocketFactory();
        } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException |
                 UnrecoverableKeyException | KeyManagementException e) {
            throw new UnexpectedException(e);
        }
    }

    private static X509Certificate readCertificate(Path path) {
        try (InputStream inputStream = new ByteArrayInputStream(readPem(path))) {
            return (X509Certificate) CERTIFICATE_FACTORY.generateCertificate(inputStream);
        } catch (IOException | CertificateException e) {
            throw new UnexpectedException(e);
        }
    }

    private static PrivateKey readPrivateKey(Path path) {
        final PKCS8EncodedKeySpec encodedKeySpec = new PKCS8EncodedKeySpec(
                readPem(path)
        );

        try {
            return RSA_KEY_FACTORY.generatePrivate(encodedKeySpec);
        } catch (InvalidKeySpecException e) {
            try {
                // try with EC key factory
                return ECDSA_KEY_FACTORY.generatePrivate(encodedKeySpec);
            } catch (InvalidKeySpecException e2) {
                throw new UnexpectedException("Cannot read as RSA or ECDSA private key", e);
            }
        }
    }

    private static byte[] readPem(Path path) {
        try (
                PemReader pemReader = new PemReader(
                        new InputStreamReader(
                                Files.newInputStream(path)
                        )
                );
        ) {
            return pemReader.readPemObject().getContent();
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }
}
