/*
 * OpenGr8on, open source extensions to systems based on Grenton devices
 * Copyright (C) 2023 Piotr Sobiech
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package pl.psobiech.opengr8on.vclu;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import org.bouncycastle.util.io.pem.PemReader;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;
import pl.psobiech.opengr8on.util.RandomUtil;

public class TlsUtil {
    static {
        // System.setProperty("javax.net.debug", "all");
    }

    static SSLSocketFactory getSocketFactory(Path caCertificatePath, Path clientCertificatePath, Path clientKeyPath) {
        try {
            final KeyStore caKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            caKeyStore.load(null, null);
            caKeyStore.setCertificateEntry("certificate", readCertificate(caCertificatePath));

            final KeyStore clientKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            clientKeyStore.load(null, null);
            if (Files.exists(clientCertificatePath) && Files.exists(clientKeyPath)) {
                final X509Certificate clientCertificate = readCertificate(clientCertificatePath);
                clientKeyStore.setCertificateEntry("certificate", clientCertificate);
                clientKeyStore.setKeyEntry("key", readPrivateKey(clientKeyPath), null, new java.security.cert.Certificate[] {clientCertificate});
            }

            final KeyManagerFactory clientKeyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            clientKeyManagerFactory.init(clientKeyStore, null);

            final TrustManagerFactory caTrustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            caTrustManagerFactory.init(caKeyStore);

            final SSLContext context = SSLContext.getInstance("TLSv1.2");
            context.init(clientKeyManagerFactory.getKeyManagers(), caTrustManagerFactory.getTrustManagers(), RandomUtil.random(true));

            return context.getSocketFactory();
        } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException | UnrecoverableKeyException | KeyManagementException e) {
            throw new UnexpectedException(e);
        }
    }

    private static X509Certificate readCertificate(Path path) {
        try (InputStream inputStream = new ByteArrayInputStream(readPem(path))) {
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                                                       .generateCertificate(inputStream);
        } catch (IOException | CertificateException e) {
            throw new UnexpectedException(e);
        }
    }

    private static PrivateKey readPrivateKey(Path path) {
        final PKCS8EncodedKeySpec encodedKeySpec = new PKCS8EncodedKeySpec(
            readPem(path)
        );

        try {
            return KeyFactory.getInstance("RSA")
                             .generatePrivate(encodedKeySpec);
        } catch (InvalidKeySpecException e) {
            try {
                return KeyFactory.getInstance("ECDSA")
                                 .generatePrivate(encodedKeySpec);
            } catch (InvalidKeySpecException | NoSuchAlgorithmException e2) {
                throw new UnexpectedException(e);
            }
        } catch (NoSuchAlgorithmException e) {
            throw new UnexpectedException(e);
        }
    }

    private static byte[] readPem(Path path) {
        try (
            InputStream inputStream = Files.newInputStream(path);
            InputStreamReader streamReader = new InputStreamReader(inputStream);
            PemReader pemReader = new PemReader(streamReader);
        ) {
            return pemReader.readPemObject().getContent();
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }
}
