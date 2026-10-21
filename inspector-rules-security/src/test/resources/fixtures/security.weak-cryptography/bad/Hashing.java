package com.acme.crypto;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Cipher;
import org.apache.commons.codec.digest.DigestUtils;

class Hashing {

    byte[] fingerprint(byte[] data) throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("MD5").digest(data);
    }

    byte[] legacy(byte[] data) throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("sha-1").digest(data);
    }

    Cipher legacyCipher() throws Exception {
        return Cipher.getInstance("DES/CBC/PKCS5Padding");
    }

    Cipher defaultMode() throws Exception {
        return Cipher.getInstance("AES");
    }

    String checksum(String text) {
        return DigestUtils.md5Hex(text);
    }

    String springChecksum(byte[] data) {
        return org.springframework.util.DigestUtils.md5DigestAsHex(data);
    }
}
