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

package pl.psobiech.opengr8on.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;

public class CipherKey {
    static {
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
    }

    public static final int INPUT_BUFFER_SIZE = 256;

    private static final String ALGORITHM = "AES";

    private static final String CIPHER = "AES/CBC/PKCS5Padding";

    protected static final byte[] DEFAULT_KEY = Base64.decodeBase64("hd5SHpxl0N5+WEXTXlPQmw==");

    protected static final byte[] DEFAULT_IV = Base64.decodeBase64("ua/jh/kZo9Og15rejhGhFg==");

    protected static final byte[] DEFAULT_BROADCAST_IV = Base64.decodeBase64("BwYFBAMCAQAEAgkDBAEFBw==");

    public static final CipherKey DEFAULT_BROADCAST = new CipherKey(CipherKey.DEFAULT_KEY, CipherKey.DEFAULT_BROADCAST_IV);

    private final SecretKeySpec keySpecification;

    private final IvParameterSpec ivParameterSpecification;

    public CipherKey(byte[] keyAsBytes, byte[] ivAsBytes) {
        this(new SecretKeySpec(keyAsBytes, ALGORITHM), new IvParameterSpec(ivAsBytes));
    }

    public CipherKey(byte[] keyAsBytes, IvParameterSpec ivParameterSpecification) {
        this(new SecretKeySpec(keyAsBytes, ALGORITHM), ivParameterSpecification);
    }

    public CipherKey(SecretKeySpec keySpecification, byte[] ivAsBytes) {
        this(keySpecification, new IvParameterSpec(ivAsBytes));
    }

    public CipherKey(SecretKeySpec keySpecification, IvParameterSpec ivParameterSpecification) {
        this.keySpecification = keySpecification;
        this.ivParameterSpecification = ivParameterSpecification;
    }

    public static CipherKey getInitialCipherKey(byte[] iv, byte[] privateKey) {
        return new CipherKey(generateDefaultKey(privateKey), iv);
    }

    public static byte[] generateDefaultKey(byte[] privateKey) {
        return generateKey(DEFAULT_IV, privateKey);
    }

    public static byte[] generateKey(byte[] iv, byte[] privateKey) {
        assert iv.length % 2 == 0;
        assert privateKey.length == iv.length / 2;

        final int size = iv.length;
        final byte[] result = new byte[size];

        int i = 0;
        for (; i < iv.length / 2; i++) {
            result[i] = (byte) (iv[i] ^ privateKey[i]);
        }
        for (; i < size; i++) {
            result[i] = (byte) (iv[i] ^ privateKey[(size - 1) - i]);
        }

        return result;
    }

    public CipherKey withIV(byte[] iv) {
        return new CipherKey(keySpecification(), new IvParameterSpec(iv));
    }

    public Optional<byte[]> decrypt(byte[] input) {
        return decrypt(input, 0, input.length);
    }

    public Optional<byte[]> decrypt(byte[] input, int offset, int limit) {
        try {
            final Cipher cipher = Cipher.getInstance(CIPHER, BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.DECRYPT_MODE, keySpecification(), ivSpecification());

            return Optional.of(
                process(cipher, input, offset, limit)
            );
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            return Optional.empty();
        } catch (InvalidAlgorithmParameterException | NoSuchPaddingException | ShortBufferException | NoSuchAlgorithmException | InvalidKeyException |
                 NoSuchProviderException e) {
            throw new UnexpectedException(e);
        }
    }

    public byte[] encrypt(byte[] message) {
        try {
            final Cipher cipher = Cipher.getInstance(CIPHER, BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.ENCRYPT_MODE, keySpecification(), ivSpecification());

            return process(cipher, message, 0, message.length);
        } catch (InvalidAlgorithmParameterException | NoSuchPaddingException | ShortBufferException | IllegalBlockSizeException | NoSuchAlgorithmException |
                 BadPaddingException | InvalidKeyException | NoSuchProviderException e) {
            throw new UnexpectedException(e);
        }
    }

    private static byte[] process(Cipher cipher, byte[] input, int offset, int limit)
        throws IllegalBlockSizeException, ShortBufferException, BadPaddingException {
        final byte[] inputBuffer = new byte[INPUT_BUFFER_SIZE];
        byte[] outputBuffer = new byte[cipher.getOutputSize(inputBuffer.length)];
        try (
            InputStream inputStream = new ByteArrayInputStream(input, offset, limit);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(cipher.getOutputSize(input.length));
        ) {
            int read;
            do {
                read = inputStream.read(inputBuffer, 0, inputBuffer.length);
                if (read < 0) {
                    outputBuffer = ensureCapacity(outputBuffer, cipher.getOutputSize(0));
                    final int written = cipher.doFinal(outputBuffer, 0);
                    if (written > 0) {
                        outputStream.write(outputBuffer, 0, written);
                    }

                    break;
                }

                outputBuffer = ensureCapacity(outputBuffer, cipher.getOutputSize(read));
                final int written = cipher.update(inputBuffer, 0, read, outputBuffer, 0);
                if (written > 0) {
                    outputStream.write(outputBuffer, 0, written);
                }
            } while (!Thread.interrupted());

            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }

    private static byte[] ensureCapacity(byte[] buffer, int requiredCapacity) {
        if (requiredCapacity > buffer.length) {
            return new byte[requiredCapacity];
        }

        return buffer;
    }

    public byte[] getSecretKey() {
        return keySpecification().getEncoded();
    }

    protected SecretKeySpec keySpecification() {
        return keySpecification;
    }

    public byte[] getIV() {
        return ivSpecification().getIV();
    }

    protected IvParameterSpec ivSpecification() {
        return ivParameterSpecification;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof final CipherKey cipherKey)) {
            return false;
        }

        return Arrays.equals(keySpecification.getEncoded(), cipherKey.keySpecification.getEncoded())
            && Arrays.equals(ivParameterSpecification.getIV(), cipherKey.ivParameterSpecification.getIV());
    }

    @Override
    public int hashCode() {
        return Objects.hash(keySpecification, ivParameterSpecification);
    }

    @Override
    public String toString() {
        return "CipherKey{" +
            "key=" + Base64.encodeBase64String(keySpecification.getEncoded()) +
            ", iv=" + Base64.encodeBase64String(ivParameterSpecification.getIV()) +
            '}';
    }
}
