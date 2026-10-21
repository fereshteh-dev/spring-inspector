package com.acme.crypto;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Cipher;
import org.apache.commons.codec.digest.DigestUtils;

class Hashing {

    byte[] fingerprint(byte[] data) throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }

    byte[] configured(byte[] data, String algorithm) throws NoSuchAlgorithmException {
        return MessageDigest.getInstance(algorithm).digest(data);
    }

    Cipher cipher() throws Exception {
        return Cipher.getInstance("AES/GCM/NoPadding");
    }

    String checksum(String text) {
        return DigestUtils.sha256Hex(text);
    }

    String unrelated(Hasher hasher, String text) {
        return hasher.md5Hex(text);
    }
}
