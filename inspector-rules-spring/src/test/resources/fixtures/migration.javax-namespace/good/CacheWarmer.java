package com.acme.cache;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import javax.annotation.Nullable;
import javax.crypto.Cipher;

class CacheWarmer {

    private final EntityManager entityManager;

    CacheWarmer(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @PostConstruct
    void warm() {}

    @Nullable
    String user(HttpServletRequest request) {
        return request.getRemoteUser();
    }
}
