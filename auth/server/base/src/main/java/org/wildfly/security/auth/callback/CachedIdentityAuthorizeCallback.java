/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.security.auth.callback;

import static org.wildfly.common.Assert.checkNotNullParam;

import java.security.Principal;
import java.util.Set;
import java.util.function.Function;

import javax.security.auth.Subject;

import org.wildfly.common.Assert;
import org.wildfly.security.auth.principal.NamePrincipal;
import org.wildfly.security.auth.server.RealmIdentity;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.auth.server.SecurityIdentity;
import org.wildfly.security.cache.CachedIdentity;
import org.wildfly.security.cache.IdentityCache;

/**
 * <p>A callback that is capable of perform authorization based on the identities managed by an {@link IdentityCache}.
 *
 * <p>This callback can be used in two ways:
 *
 * <ul>
 *     <li>As an alternative to {@link javax.security.sasl.AuthorizeCallback}. As a result, the identity (if successfully authorized) will be cached</li>
 *     <li>To perform a lookup in the cache and authorize the cached identity locally</li>
 * </ul>
 *
 * @author <a href="mailto:psilva@redhat.com">Pedro Igor</a>
 */
public class CachedIdentityAuthorizeCallback implements ExtendedCallback {

    private final Function<SecurityDomain, IdentityCache> identityCache;
    private final boolean localCache;
    private Principal principal;
    private boolean authorized;
    private SecurityDomain securityDomain;

    /**
     * Creates a new instance in order to authorize identities managed by the given <code>identityCache</code>.
     *
     * @param identityCache the identity cache
     */
    public CachedIdentityAuthorizeCallback(IdentityCache identityCache) {
        this(identityCache, false);
    }

    /**
     * Creates a new instance in order to authorize identities managed by the given <code>identityCache</code>.
     *
     * @param identityCache the identity cache
     * @param localCache if true, indicates that authorization should be based on the given {@code identityCache} only. In case the mechanism
     *                   performing the authorization is wrapped by another one that provides a top-level cache (eg.: SSO), only the given
     *                   {@code identityCache} will be considered.
     */
    public CachedIdentityAuthorizeCallback(IdentityCache identityCache, boolean localCache) {
        this(securityDomain1 -> identityCache, localCache);
    }

    /**
     * <p>Creates a new instance in order to authorize identities managed by the given <code>identityCache</code>.
     *
     * <p>This constructor can be used to perform caching operations (e.g.: put, get and remove) in the context of a {@link SecurityDomain}.
     *
     * @param identityCache a function that creates an {@link IdentityCache} given a {@link SecurityDomain}
     * @param localCache if true, indicates that authorization should be based on the given {@code identityCache} only. In case the mechanism
     *                   performing the authorization is wrapped by another one that provides a top-level cache (eg.: SSO), only the given
     *                   {@code identityCache} will be considered.
     */
    public CachedIdentityAuthorizeCallback(Function<SecurityDomain, IdentityCache> identityCache, boolean localCache) {
        checkNotNullParam("identityCache", identityCache);
        this.identityCache = identityCache;
        this.localCache = localCache;
    }

    /**
     * Creates a new instance to authenticate, authorize and cache the identity associated with the given <code>name</code>.
     *
     * @param name the name associated with the identity
     * @param identityCache the identity cache
     */
    public CachedIdentityAuthorizeCallback(String name, IdentityCache identityCache) {
        this(new NamePrincipal(name), identityCache);
    }

    /**
     * Creates a new instance to authenticate, authorize and cache the identity associated with the given <code>principal</code>.
     *
     * @param principal the principal associated with the identity
     * @param identityCache the identity cache
     * @param localCache if true, indicates that authorization should be based on the given {@code identityCache} only. In case the mechanism
     *                   performing the authorization is wrapped by another one that provides a top-level cache (eg.: SSO), only the given
     *                   {@code identityCache} will be considered.
     */
    public CachedIdentityAuthorizeCallback(Principal principal, IdentityCache identityCache, boolean localCache) {
        this(principal, securityDomain -> identityCache, localCache);
    }

    /**
     * Creates a new instance to authenticate, authorize and cache the identity associated with the given <code>principal</code>.
     *
     * @param principal the principal associated with the identity
     * @param identityCache the identity cache
     */
    public CachedIdentityAuthorizeCallback(Principal principal, IdentityCache identityCache) {
        this(principal, securityDomain -> identityCache, false);
    }

    /**
     * <p>Creates a new instance to authenticate, authorize and cache the identity associated with the given <code>principal</code>.
     *
     * <p>This constructor can be used to perform caching operations (e.g.: put, get and remove) in the context of a {@link SecurityDomain}.
     *
     * @param principal the principal associated with the identity
     * @param identityCache a function that creates an {@link IdentityCache} given a {@link SecurityDomain}
     * @param localCache if true, indicates that authorization should be based on the given {@code identityCache} only. In case the mechanism
     *                   performing the authorization is wrapped by another one that provides a top-level cache (eg.: SSO), only the given
     *                   {@code identityCache} will be considered.
     */
    public CachedIdentityAuthorizeCallback(Principal principal, Function<SecurityDomain, IdentityCache> identityCache, boolean localCache) {
        checkNotNullParam("principal", principal);
        checkNotNullParam("identityCache", identityCache);
        this.principal = principal;
        this.identityCache = identityCache;
        this.localCache = localCache;
    }

    /**
     * Set the Roles present on {@link CachedIdentity} into the {@link RealmIdentity#setSubject(Subject)} in order to get authenticate on all HA nodes;
     * @param realmIdentity
     */
    public void setSubject(RealmIdentity realmIdentity) {
        checkNotNullParam("realmIdentity", realmIdentity);
        Subject subject = realmIdentity.getSubject();
        if (subject == null) {
            CachedIdentity cachedIdentity = createDomainCache().get();
            if (cachedIdentity != null) {
                subject = new Subject();
                Set<Principal> principals = subject.getPrincipals();
                principals.add(realmIdentity.getRealmIdentityPrincipal());
                cachedIdentity.getRoles().forEach(role -> principals.add(new Roles(role)));
                realmIdentity.setSubject(subject);
            }
        }
    }

    /**
     * Indicates if a cached identity was successfully authorized.
     *
     * @return true if the cached identity was successfully authorized. Otherwise, false
     */
    public boolean isAuthorized() {
        return authorized;
    }

    /**
     * Authorizes and caches the given <code>securityIdentity</code>.
     *
     * @param securityIdentity the identity to authorize and cache. If null, the corresponding identity will be removed from the cache
     */
    public void setAuthorized(SecurityIdentity securityIdentity) {
        authorized = securityIdentity != null;
        if (authorized) {
            createDomainCache().put(securityIdentity);
        } else {
            createDomainCache().remove();
        }
    }

    /**
     * Returns the {@link Principal} representing the cached identity.
     *
     * @return the principal (not {@code null})
     */
    public Principal getPrincipal() {
        CachedIdentity cachedIdentity = createDomainCache().get();
        if (cachedIdentity != null) {
            return new NamePrincipal(cachedIdentity.getName());
        }
        return null;
    }

    /**
     * Returns the authorization {@link Principal}.
     *
     * @return the principal (not {@code null})
     */
    public Principal getAuthorizationPrincipal() {
        return this.principal;
    }

    /**
     * Returns a cached {@link SecurityIdentity}, if present in the cache.
     *
     * @return the cached identity or null if there is no entry in the cache
     */
    public SecurityIdentity getIdentity() {
        CachedIdentity cachedIdentity = createDomainCache().get();
        if (cachedIdentity != null) {
            return cachedIdentity.getSecurityIdentity();
        }
        return null;
    }

    /**
     * Indicates if authorization decisions should be performed based on the given {@link IdentityCache} only.
     *
     * @return true indicating that authorization decisions should be performed based on the given {@link IdentityCache} only. Otherwise, false
     */
    public boolean isLocalCache() {
        return localCache;
    }

    /**
     * Set the current {@link SecurityDomain} in order to obtain identities from the cache
     *
     * @param securityDomain the current security domain
     */
    public void setSecurityDomain(SecurityDomain securityDomain) {
        Assert.checkNotNullParam("securityDomain", securityDomain);
        this.securityDomain = securityDomain;
    }

    @Override
    public boolean isOptional() {
        return false;
    }

    @Override
    public boolean needsInformation() {
        return false;
    }

    private IdentityCache createDomainCache() {
        return this.identityCache.apply(securityDomain);
    }

    private static class Roles implements Principal {

        private final String name;

        Roles(final String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return this.name;
        }
    }

}