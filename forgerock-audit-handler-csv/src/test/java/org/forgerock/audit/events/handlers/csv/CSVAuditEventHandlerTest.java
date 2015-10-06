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

package org.forgerock.audit.events.handlers.csv;

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.audit.AuditServiceBuilder.newAuditService;
import static org.forgerock.json.JsonValue.*;
import static org.forgerock.util.test.assertj.AssertJPromiseAssert.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.forgerock.audit.AuditService;
import org.forgerock.audit.AuditServiceBuilder;
import org.forgerock.audit.events.handlers.AuditEventHandler;
import org.forgerock.audit.events.handlers.EventHandlerConfiguration.EventBufferingConfiguration;
import org.forgerock.audit.events.handlers.csv.CSVAuditEventHandlerConfiguration.CsvSecurity;
import org.forgerock.audit.json.AuditJsonConfig;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.CreateRequest;
import org.forgerock.json.resource.QueryFilters;
import org.forgerock.json.resource.QueryRequest;
import org.forgerock.json.resource.QueryResourceHandler;
import org.forgerock.json.resource.QueryResponse;
import org.forgerock.json.resource.ReadRequest;
import org.forgerock.json.resource.Requests;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResourceResponse;
import org.forgerock.util.encode.Base64;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.ResultHandler;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

@SuppressWarnings("javadoc")
public class CSVAuditEventHandlerTest {

    /**
     * Integration test.
     */
    @Test
    public void canConfigureSyslogHandlerFromJsonAndRegisterWithAuditService() throws Exception {
        // given
        final AuditServiceBuilder auditServiceBuilder = newAuditService();
        final JsonValue config = AuditJsonConfig.getJson(getResource("/event-handler-config.json"));

        // when
        AuditJsonConfig.registerHandlerToService(config, auditServiceBuilder);

        // then
        AuditService auditService = auditServiceBuilder.build();
        auditService.startup();
        AuditEventHandler<?> registeredHandler = auditService.getRegisteredHandler("csv");
        assertThat(registeredHandler).isNotNull();
    }

    private InputStream getResource(String resourceName) {
        return getClass().getResourceAsStream(resourceName);
    }

    @Test
    public void testCreatingAuditLogEntryWithBuffering() throws Exception {
        //given
        final Path logDirectory = Files.createTempDirectory("CSVAuditEventHandlerTest");
        logDirectory.toFile().deleteOnExit();
        final CSVAuditEventHandler csvHandler = new CSVAuditEventHandler();
        csvHandler.configure(getConfigWithBuffering(logDirectory));
        addEventsMetaData(csvHandler);
        final Context context = new RootContext();
        try {

            // when
            csvHandler.publishEvent(context, "access", buildEvent(1));
            csvHandler.publishEvent(context, "access", buildEvent(2));

            // then
            final String expectedContent = "\"_id\",\"timestamp\",\"transactionId\"\n"
                    + "\"_id1\",\"timestamp\",\"transactionId-X\"\n" + "\"_id2\",\"timestamp\",\"transactionId-X\"";
            final File file = logDirectory.resolve("access.csv").toFile();
            int tries = 0;
            while ((!file.exists() || file.length() < expectedContent.length()) && tries < 10) {
                Thread.sleep(10);
                tries++;
            }
            assertThat(file).hasContent(expectedContent);
        } finally {
            csvHandler.shutdown();
        }
    }

    @Test
    public void testCreatingAuditLogEntry() throws Exception {
        //given
        final Path logDirectory = Files.createTempDirectory("CSVAuditEventHandlerTest");
        logDirectory.toFile().deleteOnExit();
        final CSVAuditEventHandler csvHandler = createAndConfigureHandler(logDirectory, true);
        final Context context = new RootContext();

        final CreateRequest createRequest = makeCreateRequest();

        //when
        final Promise<ResourceResponse, ResourceException> promise =
                csvHandler.publishEvent(context, "access", createRequest.getContent());

        //then
        assertThat(promise)
                .succeeded()
                .withObject()
                .isInstanceOf(ResourceResponse.class);

        // TODO should use AssertJResourceResponseAssert
        final ResourceResponse resource = promise.get();
        assertThat(resource).isNotNull();
        assertThat(resource.getContent().asMap()).isEqualTo(createRequest.getContent().asMap());
    }

    @Test
    public void testReadingAuditLogEntry() throws Exception {
        //given
        final Path logDirectory = Files.createTempDirectory("CSVAuditEventHandlerTest");
        logDirectory.toFile().deleteOnExit();
        final CSVAuditEventHandler csvHandler = createAndConfigureHandler(logDirectory, true);
        final Context context = new RootContext();

        final ResourceResponse event = createAccessEvent(csvHandler);

        final ReadRequest readRequest = Requests.newReadRequest("access", event.getId());

        //when
        final Promise<ResourceResponse, ResourceException> promise =
                csvHandler.readEvent(context, "access", readRequest.getResourcePathObject().tail(1).toString());

        //then
        assertThat(promise)
                .succeeded()
                .withObject()
                .isInstanceOf(ResourceResponse.class);

        // TODO should use AssertJResourceResponseAssert
        final ResourceResponse resource = promise.get();
        assertResourceEquals(resource, event);
    }

    private static void assertResourceEquals(ResourceResponse left, ResourceResponse right) {
        Map<String, Object> leftAsMap = dropNullEntries(left.getContent()).asMap();
        Map<String, Object> rightAsMap = dropNullEntries(right.getContent()).asMap();
        assertThat(leftAsMap).isEqualTo(rightAsMap);
    }

    private static JsonValue dropNullEntries(JsonValue jsonValue) {
        JsonValue result = jsonValue.clone();

        for(String key : jsonValue.keys()) {
            if (jsonValue.get(key).isNull()) {
                result.remove(key);
            }
        }

        return result;
    }

    @Test
    public void testQueryOnAuditLogEntry() throws Exception{
        //given
        final Path logDirectory = Files.createTempDirectory("CSVAuditEventHandlerTest");
        logDirectory.toFile().deleteOnExit();
        final CSVAuditEventHandler csvHandler = createAndConfigureHandler(logDirectory, true);
        final Context context = new RootContext();

        final QueryResourceHandler queryResourceHandler = mock(QueryResourceHandler.class);
        final ArgumentCaptor<ResourceResponse> resourceCaptor = ArgumentCaptor.forClass(ResourceResponse.class);

        final ResourceResponse event = createAccessEvent(csvHandler);

        final QueryRequest queryRequest = Requests.newQueryRequest("access")
                .setQueryFilter(QueryFilters.parse("/_id eq \"_id0\""));
        //when
        final Promise<QueryResponse, ResourceException> promise =
                csvHandler.queryEvents(context, "access", queryRequest, queryResourceHandler);

        //then
        assertThat(promise).succeeded();
        verify(queryResourceHandler).handleResource(resourceCaptor.capture());

        final ResourceResponse resource = resourceCaptor.getValue();
        assertResourceEquals(resource, event);
    }

    private CreateRequest makeCreateRequest() {
        return Requests.newCreateRequest("access", buildEvent());
    }

    private JsonValue buildEvent() {
        return buildEvent(0);
    }

    private JsonValue buildEvent(int index) {
        final JsonValue content = json(
                object(
                        field("_id", "_id" + index),
                        field("timestamp", "timestamp"),
                        field("transactionId", "transactionId-X")
                        )
                );
        return content;
    }

    @SuppressWarnings("unchecked")
    private static <T> ResultHandler<T> mockResultHandler(Class<T> type) {
        return mock(ResultHandler.class);
    }

    private ResourceResponse createAccessEvent(AuditEventHandler<?> auditEventHandler) throws Exception {
        final CreateRequest createRequest = makeCreateRequest();
        final Context context = new RootContext();

        final Promise<ResourceResponse, ResourceException> promise =
                auditEventHandler.publishEvent(context, "access", createRequest.getContent());

        assertThat(promise)
                .succeeded()
                .isInstanceOf(ResourceResponse.class);

        // TODO should use AssertJResourceResponseAssert
        return promise.get();
    }

    @Test
    public void testCreateCsvLogEntryWritesToFile() throws Exception {
        final Path logDirectory = Files.createTempDirectory("CSVAuditEventHandlerTest");
        logDirectory.toFile().deleteOnExit();
        final CSVAuditEventHandler csvHandler = createAndConfigureHandler(logDirectory, true);
        final Context context = new RootContext();

        final JsonValue content = json(
                object(
                        field("_id", "1"),
                        field("timestamp", "123456"),
                        field("transactionId", "A10000")));
        final CreateRequest createRequest = Requests.newCreateRequest("access", content);

        csvHandler.publishEvent(context, "access", createRequest.getContent());

        final String expectedContent = "\"_id\",\"timestamp\",\"transactionId\",\"HMAC\"\n"
                + "\"1\",\"123456\",\"A10000\",\"l3jKX9DpKEWpALEBefJxOUKtLQttianWfqISvnk2HgE=\"";
        assertThat(logDirectory.resolve("access.csv").toFile()).hasContent(expectedContent);
    }

    private CSVAuditEventHandler createAndConfigureHandler(Path tempDirectory, boolean enableSecurity)
            throws Exception {
        CSVAuditEventHandler handler = spy(new CSVAuditEventHandler());
        CSVAuditEventHandlerConfiguration config = new CSVAuditEventHandlerConfiguration();
        config.setLogDirectory(tempDirectory.toString());

        if (enableSecurity) {
            config.setSecurity(getCsvSecurityConfig());
        }

        handler.configure(config);
        addEventsMetaData(handler);
        return handler;
    }

    private CsvSecurity getCsvSecurityConfig() throws Exception {
        CSVAuditEventHandlerConfiguration.CsvSecurity csvSecurity = new CSVAuditEventHandlerConfiguration.CsvSecurity();
        csvSecurity.setEnabled(true);
        final String keystorePath = new File(System.getProperty("java.io.tmpdir"), "secure-audit.jks").getAbsolutePath();
        csvSecurity.setFilename(keystorePath);
        csvSecurity.setPassword("forgerock");

        // Force the initial key so we'll have reproductible builds.
        SecretKey secretKey = new SecretKeySpec(Base64.decode("zmq4EoprX52XLGyLkMENcin0gv0jwYyrySi3YOqfhFY="), "RAW");
        HmacCalculator.writeToKeyStore(secretKey, csvSecurity.getFilename(), "InitialKey", csvSecurity.getPassword());

        return csvSecurity;
    }

    /** Returns a configuration with buffering enabled. */
    private CSVAuditEventHandlerConfiguration getConfigWithBuffering(Path tempDir) {
        EventBufferingConfiguration config = new EventBufferingConfiguration();
        config.setEnabled(true);
        CSVAuditEventHandlerConfiguration handlerConfig = new CSVAuditEventHandlerConfiguration();
        handlerConfig.setLogDirectory(tempDir.toString());
        handlerConfig.setBufferingConfiguration(config);
        return handlerConfig;
    }

    private void addEventsMetaData(CSVAuditEventHandler handler) throws Exception {
        Map<String, JsonValue> events = new LinkedHashMap<>();
        try (final InputStream configStream = getClass().getResourceAsStream("/events.json")) {
            final JsonValue predefinedEventTypes = new JsonValue(new ObjectMapper().readValue(configStream, Map.class));
            for (String eventTypeName : predefinedEventTypes.keys()) {
                events.put(eventTypeName, predefinedEventTypes.get(eventTypeName));
            }
        }
        handler.setAuditEventsMetaData(events);
    }

}
