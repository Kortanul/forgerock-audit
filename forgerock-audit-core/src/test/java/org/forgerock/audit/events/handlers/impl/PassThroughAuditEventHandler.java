/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2015 ForgeRock AS.
 */

package org.forgerock.audit.events.handlers.impl;

import org.forgerock.audit.events.handlers.AuditEventHandlerBase;
import org.forgerock.audit.util.ResourceExceptionsUtil;
import org.forgerock.http.Context;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.ActionResponse;
import org.forgerock.json.resource.CreateRequest;
import org.forgerock.json.resource.QueryRequest;
import org.forgerock.json.resource.QueryResourceHandler;
import org.forgerock.json.resource.QueryResponse;
import org.forgerock.json.resource.ReadRequest;
import org.forgerock.json.resource.ResourceResponse;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.Responses;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;

/**
 * Handles AuditEvents by just calling the result Handler.
 */
public class PassThroughAuditEventHandler extends AuditEventHandlerBase<PassThroughAuditEventHandlerConfiguration> {

    /** A message logged when a new entry is added. */
    private String message;

    @Override
    public void configure(PassThroughAuditEventHandlerConfiguration config) throws ResourceException {
        this.message = config.getMessage();
    }

    /** {@inheritDoc} */
    @Override
    public void close() throws ResourceException {
        // nothing to do
    }

    /**
     * Perform an action on the audit log.
     * {@inheritDoc}
     */
    @Override
    public Promise<ActionResponse, ResourceException> actionCollection(
            final Context context,
            final ActionRequest request) {
        return Promises.newExceptionPromise(ResourceExceptionsUtil.notSupported(request));
    }

    /**
     * Perform an action on the audit log entry.
     * {@inheritDoc}
     */
    @Override
    public Promise<ActionResponse, ResourceException> actionInstance(
            final Context context,
            final String resourceId,
            final ActionRequest request) {
        return Promises.newExceptionPromise(ResourceExceptionsUtil.notSupported(request));
    }

    /**
     * Create a audit log entry.
     * {@inheritDoc}
     */
    @Override
    public Promise<ResourceResponse, ResourceException> createInstance(
            final Context context,
            final CreateRequest request) {
        return Promises.newResultPromise(
                Responses.newResourceResponse(
                        request.getContent().get(ResourceResponse.FIELD_CONTENT_ID).asString(),
                        null,
                        new JsonValue(request.getContent())));
    }

    /**
     * Perform a query on the audit log.
     * {@inheritDoc}
     */
    @Override
    public Promise<QueryResponse, ResourceException> queryCollection(
            final Context context,
            final QueryRequest request,
            final QueryResourceHandler handler) {
        return Promises.newResultPromise(Responses.newQueryResponse());
    }

    /**
     * Read from the audit log.
     * {@inheritDoc}
     */
    @Override
    public Promise<ResourceResponse, ResourceException> readInstance(
            final Context context,
            final String resourceId,
            final ReadRequest request) {
        return Promises.newExceptionPromise(ResourceExceptionsUtil.notSupported(request));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<PassThroughAuditEventHandlerConfiguration> getConfigurationClass() {
        return PassThroughAuditEventHandlerConfiguration.class;
    }
}
