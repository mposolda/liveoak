/*
 * Copyright 2014 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at http://www.eclipse.org/legal/epl-v10.html
 */
package io.liveoak.spi.resource.async;

import io.liveoak.spi.RequestContext;
import io.liveoak.spi.state.ResourceState;
import org.jboss.logging.Logger;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Basic server-side representation of a resource.
 *
 * <p>The basic function of a resource is that it may (optionally) contain
 * children, in the form of members-of-a-collection, or properties-of-an-object.</p>
 *
 * <p>Additionally, it may optionally be deleted.</p>
 *
 * <p>All resources optionally have an ID, if they are to be directly addressable.</p>
 *
 * <p>Each resource, if it is a child of another resource, should include a non-null
 * reference to its parent</p>
 *
 * @author Bob McWhirter
 * @author Ken Finnigan
 */
public interface Resource {

    static final Logger log = Logger.getLogger(Resource.class);

    /**
     * Retrieve the URI associated with this resource.
     *
     * @return The URI
     */
    default URI uri() {
        List<String> segments = new ArrayList<>();
        Resource current = this;

        while (current != null) {
            String currentId = current.id();
            if (currentId == null) {
                return null;
            }
            try {
                // if the id starts with a ';', then don't encode this part
                int pos = currentId.indexOf(";");
                if (pos != -1) {
                    currentId = currentId.substring(0, pos+1) + URLEncoder.encode(current.id().substring(pos+1), "UTF-8");
                } else {
                    currentId = URLEncoder.encode(current.id(), "UTF-8");
                }
            } catch (UnsupportedEncodingException e) {
                // if we cant encode the uri, then log the error message and use the unencoded value
                log.error( "Error trying to encode URI value [" + currentId + "] to UTF-8. Attempting to use unencoded value", e );
            }
            segments.add(0, currentId);
            current = current.parent();
        }

        StringBuilder buf = new StringBuilder();

        if (segments.size() == 1 && segments.get(0).equals("")) {
            return URI.create("/");
        }

        boolean initialSlash = false;

        for ( String s : segments ) {
            if (s != null) {
                if (s.startsWith(";")) {
                    if ( ! initialSlash ) {
                        buf.append( "/" );
                    }
                    buf.append(s);
                } else if (!"".equals(s)) {
                    initialSlash = true;
                    buf.append("/");
                    buf.append(s);
                }
            }
        }

        return URI.create(buf.toString());
    }

    /**
     * Retrieve the parent resource of this resource, if any.
     *
     * @return The parent, or {@code null} if none.
     */
    Resource parent();

    /**
     * Retrieve the identifier of this resource, if any.
     *
     * @return The id, or {@code null} if none.
     */
    String id();

    /**
     * Read the properties of this resource.
     *
     * @param ctx  The request context.
     * @param sink The sink to capture the properties.
     */
    default void readProperties(RequestContext ctx, PropertySink sink) throws Exception {
        sink.close();
    }

    /**
     * Update this object's state.
     *
     * If we assume this resource's uri is /app/service/id then this method is invoked if
     *
     *   PUT /app/service/id
     *
     * is issued with JSON body. Presence of id property and its value in JSON body is irrelevant for invocation mechanics.
     *
     * @param state     The inbound representation of the state.
     * @param responder To respond to the action.
     */
    default void updateProperties(RequestContext ctx, ResourceState state, Responder responder) throws Exception {
        responder.updateNotSupported(this);
    }

    /**
     * Create a new child resource of this collection.
     *
     * If we assume this resource's uri is /app/service then this method is invoked if
     *
     *   POST /app/service
     *
     * is issued with JSON body. Presence of id property and its value in JSON body is irrelevant for invocation mechanics.
     *
     * @param state     The state for the child, which may include an ID.
     * @param responder To respond to the action.
     */
    default void createMember(RequestContext ctx, ResourceState state, Responder responder) throws Exception {
        responder.createNotSupported(this);
    }

    /**
     * Write the members of this object to the provided sink.
     *
     * @param sink The sink to stream members to.
     */
    default void readMembers(RequestContext ctx, ResourceSink sink) throws Exception {
        sink.close();
    }

    default void readMember(RequestContext ctx, String id, Responder responder) throws Exception {
        readMembers( ctx, new ResourceSink() {
            private boolean found = false;
            @Override
            public void close() {
                if ( ! found ) {
                    responder.noSuchResource( id );
                }
            }

            @Override
            public void accept(Resource resource) {
                if ( found ) {
                    return;
                }
                if ( resource.id().equals( id ) ) {
                    responder.resourceRead( resource );
                    found = true;
                }
            }
        });
    }

    /**
     * Delete this resource.
     *
     * @param responder To respond to the action.
     */
    default void delete(RequestContext ctx, Responder responder) throws Exception {
        responder.deleteNotSupported(this);
    }
}
