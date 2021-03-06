/*
 * Copyright 2014 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at http://www.eclipse.org/legal/epl-v10.html
 */
package io.liveoak.security.policy.uri.impl;

import java.util.Collection;

import io.liveoak.spi.ResourcePath;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class URIPolicyRule {

    private final ResourcePath resourcePath;
    private final Collection<String> requestTypes;
    private final RolesContainer rolesContainer;

    public URIPolicyRule(ResourcePath resourcePath, Collection<String> requestTypes, RolesContainer rolesContainer) {
        this.resourcePath = resourcePath;
        this.requestTypes = requestTypes;
        this.rolesContainer = rolesContainer;
    }

    public Collection<String> getRequestTypes() {
        return requestTypes;
    }

    public RolesContainer getRolesContainer() {
        return rolesContainer;
    }

    public ResourcePath getResourcePath() {
        return resourcePath;
    }

    @Override
    public String toString() {
        return new StringBuilder("URIPolicyRule [ resourcePath=").append(resourcePath)
                .append(", requestTypes=").append(requestTypes)
                .append(", rolesContainer=").append(rolesContainer)
                .append(" ]").toString();
    }
}
