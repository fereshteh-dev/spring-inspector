package com.acme.cache;

import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import javax.persistence.EntityManager;
import javax.servlet.http.HttpServletRequest;

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
