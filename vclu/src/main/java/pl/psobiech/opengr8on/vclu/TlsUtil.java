/*
 * OpenGr8ton, open source extensions to systems based on Grenton devices
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

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import org.bouncycastle.util.io.pem.PemReader;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;

public class TlsUtil {
    static SSLSocketFactory getSocketFactory(Path caCrtFile, Path crtFile, Path keyFile) {
        try {
            final KeyStore caKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            caKeyStore.load(null, null);
            caKeyStore.setCertificateEntry("ca-certificate", readCertificate(caCrtFile));

            final X509Certificate clientCertificate = readCertificate(crtFile);

            final KeyStore clientKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            clientKeyStore.load(null, null);
            clientKeyStore.setCertificateEntry("certificate", clientCertificate);
            clientKeyStore.setKeyEntry("private-key", readPrivateKey(keyFile), null, new java.security.cert.Certificate[] {clientCertificate});

            final KeyManagerFactory clientKeyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            clientKeyManagerFactory.init(clientKeyStore, null);

            final TrustManagerFactory caTrustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            caTrustManagerFactory.init(caKeyStore);

            final SSLContext context = SSLContext.getInstance("TLSv1.2");
            context.init(clientKeyManagerFactory.getKeyManagers(), caTrustManagerFactory.getTrustManagers(), null);

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
        try {
            return (PrivateKey) KeyFactory.getInstance("RSA")
                                          .generatePrivate(new PKCS8EncodedKeySpec(
                                              readPem(path)
                                          ));
        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
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
