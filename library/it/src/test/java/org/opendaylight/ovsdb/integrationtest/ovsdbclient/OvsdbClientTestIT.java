/*
 * Copyright (C) 2014 EBay Software Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Authors : Ashwin Raveendran
 */
package org.opendaylight.ovsdb.integrationtest.ovsdbclient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.opendaylight.ovsdb.lib.operations.Operations.op;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opendaylight.ovsdb.lib.MonitorCallBack;
import org.opendaylight.ovsdb.lib.OvsdbClient;
import org.opendaylight.ovsdb.lib.it.LibraryIntegrationTestBase;
import org.opendaylight.ovsdb.lib.it.LibraryIntegrationTestUtils;
import org.opendaylight.ovsdb.lib.message.MonitorRequest;
import org.opendaylight.ovsdb.lib.message.MonitorRequestBuilder;
import org.opendaylight.ovsdb.lib.message.MonitorSelect;
import org.opendaylight.ovsdb.lib.message.TableUpdate;
import org.opendaylight.ovsdb.lib.message.TableUpdates;
import org.opendaylight.ovsdb.lib.notation.Column;
import org.opendaylight.ovsdb.lib.notation.Mutator;
import org.opendaylight.ovsdb.lib.notation.Row;
import org.opendaylight.ovsdb.lib.notation.UUID;
import org.opendaylight.ovsdb.lib.operations.OperationResult;
import org.opendaylight.ovsdb.lib.operations.TransactionBuilder;
import org.opendaylight.ovsdb.lib.schema.ColumnSchema;
import org.opendaylight.ovsdb.lib.schema.DatabaseSchema;
import org.opendaylight.ovsdb.lib.schema.GenericTableSchema;
import org.opendaylight.ovsdb.lib.schema.TableSchema;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class OvsdbClientTestIT extends LibraryIntegrationTestBase {
    private static final Logger LOG = LoggerFactory.getLogger(OvsdbClientTestIT.class);
    OvsdbClient ovs;
    DatabaseSchema dbSchema = null;
    private static final String TEST_BRIDGE_NAME = "br-test";
    private static UUID testBridgeUuid = null;

    /**
     * Test general OVSDB transactions (viz., insert, select, update,
     * mutate, comment, delete, where, commit) as well as the special
     * transactions (viz., abort and assert)
     */
    @Test
    public void testTransact() throws IOException, InterruptedException, ExecutionException {
        assertNotNull(dbSchema);
        TableSchema<GenericTableSchema> bridge = dbSchema.table("Bridge", GenericTableSchema.class);
        ColumnSchema<GenericTableSchema, String> name = bridge.column("name", String.class);

        createBridgeTransaction();
        abortTransaction();
        assertTransaction();
    }

    /**
     * Test OVS monitor request and reply, with and without specific column filters,
     * for the Bridge table in the OVSDB. The setup involves creating a test bridge with 5
     * flood_vlans and 2 key-value pairs, and monitoring the DB update.
     */
    @Test
    public void testMonitorRequest() throws ExecutionException, InterruptedException, IOException {
        assertNotNull(dbSchema);
        // Create Test Bridge before testing the Monitor operation
        createBridgeTransaction();
        sendBridgeMonitorRequest(true); // Test monitor request with Column filters
        sendBridgeMonitorRequest(false); // Test monitor request without filters
    }

    public void sendBridgeMonitorRequest(boolean filter) throws ExecutionException, InterruptedException, IOException {
        assertNotNull(dbSchema);
        GenericTableSchema bridge = dbSchema.table("Bridge", GenericTableSchema.class);

        List<MonitorRequest> monitorRequests = Lists.newArrayList();
        ColumnSchema<GenericTableSchema, Set<Integer>> flood_vlans = bridge.multiValuedColumn("flood_vlans", Integer.class);
        ColumnSchema<GenericTableSchema, Map<String, String>> externalIds = bridge.multiValuedColumn("external_ids", String.class, String.class);
        ColumnSchema<GenericTableSchema, String> name = bridge.column("name", String.class);
        MonitorRequestBuilder<GenericTableSchema> builder = MonitorRequestBuilder.builder(bridge);
        if (filter) {
            builder.addColumn(bridge.column("name"))
                   .addColumn(bridge.column("fail_mode", String.class))
                   .addColumn(flood_vlans)
                   .addColumn(externalIds);
        }
        monitorRequests.add(builder.with(new MonitorSelect(true, true, true, true))
                                   .build());

        final List<Object> results = Lists.newArrayList();

        TableUpdates updates = ovs.monitor(dbSchema, monitorRequests, new MonitorCallBack() {
            @Override
            public void update(TableUpdates result, DatabaseSchema dbSchema) {
                results.add(result);
                LOG.info("result = {}", result);
            }

            @Override
            public void exception(Throwable t) {
                results.add(t);
                LOG.warn("t = ", t);
            }
        });
        if (updates != null) {
            results.add(updates);
        }
        for (int i = 0; i < 3 ; i++) { //wait 3 seconds to get a result
            LOG.info("waiting on monitor response for Bridge Table...");
            if (!results.isEmpty()) {
                break;
            }
            Thread.sleep(1000);
        }

        assertTrue(!results.isEmpty());
        Object result = results.get(0);
        assertTrue(result instanceof TableUpdates);
        updates = (TableUpdates) result;
        TableUpdate<GenericTableSchema> update = updates.getUpdate(bridge);
        assertTrue(update.getRows().size() > 0);
        for (UUID uuid : update.getRows().keySet()) {
            Row<GenericTableSchema> aNew = update.getNew(uuid);
            if (!aNew.getColumn(name).getData().equals(TEST_BRIDGE_NAME)) {
                continue;
            }
            if (filter) {
                assertEquals(builder.getColumns().size(), aNew.getColumns().size());
            } else {
                // As per RFC7047, Section 4.1.5 : If "columns" is omitted, all columns in the table, except for "_uuid", are monitored.
                assertEquals(bridge.getColumns().size() - 1, aNew.getColumns().size());
            }
            for (Column<GenericTableSchema, ?> column: aNew.getColumns()) {
                if (column.getSchema().equals(flood_vlans)) {
                    // Test for the 5 flood_vlans inserted in Bridge br-test in createBridgeTransaction
                    Set<Integer> data = column.getData(flood_vlans);
                    assertNotNull(data);
                    assertTrue(!data.isEmpty());
                    assertEquals(5, data.size());
                } else if (column.getSchema().equals(externalIds)) {
                    // Test for the {"key", "value"} external_ids inserted in Bridge br-test in createBridgeTransaction
                    Map<String, String> data = column.getData(externalIds);
                    assertNotNull(data);
                    assertNotNull(data.get("key"));
                    assertEquals("value", data.get("key"));
                    // Test for {"key2", "value2"} external_ids mutation-inserted in Bridge br-test in createBridgeTransaction
                    assertNotNull(data.get("key2"));
                    assertEquals("value2", data.get("key2"));
                }
            }
            return;
        }
        fail("Bridge being monitored :"+ TEST_BRIDGE_NAME +" Not found");
    }

    /*
     * TODO : selectOpenVSwitchTableUuid method isn't working as expected due to the Jackson
     * parsing challenges on the Row object returned by the Select operation.
     */
    private UUID selectOpenVSwitchTableUuid() throws ExecutionException, InterruptedException {
        assertNotNull(dbSchema);
        GenericTableSchema ovsTable = dbSchema.table("Open_vSwitch", GenericTableSchema.class);

        List<MonitorRequest> monitorRequests = Lists.newArrayList();
        ColumnSchema<GenericTableSchema, UUID> _uuid = ovsTable.column("_uuid", UUID.class);

        List<OperationResult> results = ovs.transactBuilder(dbSchema)
               .add(op.select(ovsTable)
                      .column(_uuid))
                      .execute()
                      .get();

        assertTrue(!results.isEmpty());
        OperationResult result = results.get(0);
        List<Row<GenericTableSchema>> rows = result.getRows();
        Row<GenericTableSchema> ovsTableRow = rows.get(0);
        return ovsTableRow.getColumn(_uuid).getData();
    }

    private void createBridgeTransaction() throws IOException, InterruptedException, ExecutionException {
        assertNotNull(dbSchema);
        TableSchema<GenericTableSchema> bridge = dbSchema.table("Bridge", GenericTableSchema.class);
        GenericTableSchema ovsTable = dbSchema.table("Open_vSwitch", GenericTableSchema.class);

        ColumnSchema<GenericTableSchema, String> name = bridge.column("name", String.class);
        ColumnSchema<GenericTableSchema, String> fail_mode = bridge.column("fail_mode", String.class);
        ColumnSchema<GenericTableSchema, Set<Integer>> flood_vlans = bridge.multiValuedColumn("flood_vlans", Integer.class);
        ColumnSchema<GenericTableSchema, Map<String, String>> externalIds = bridge.multiValuedColumn("external_ids", String.class, String.class);
        ColumnSchema<GenericTableSchema, Set<UUID>> bridges = ovsTable.multiValuedColumn("bridges", UUID.class);
        ColumnSchema<GenericTableSchema, UUID> _uuid = ovsTable.column("_uuid", UUID.class);

        String namedUuid = "br_test";
        int insertOperationIndex = 0;
        UUID parentTable = selectOpenVSwitchTableUuid();
        TransactionBuilder transactionBuilder = ovs.transactBuilder(dbSchema)
                 /*
                  * Make sure that the position of insert operation matches the insertOperationIndex.
                  * This will be used later when the Results are processed.
                  */
                .add(op.insert(bridge)
                        .withId(namedUuid)
                        .value(name, TEST_BRIDGE_NAME)
                        .value(flood_vlans, Sets.newHashSet(100, 101, 4001))
                        .value(externalIds, ImmutableMap.of("key","value")))
                .add(op.comment("Inserting Bridge br-int"))
                .add(op.update(bridge)
                        .set(fail_mode, "secure")
                        .where(name.opEqual(TEST_BRIDGE_NAME))
                        .build())
                .add(op.select(bridge)
                        .column(name)
                        .column(_uuid)
                        .where(name.opEqual(TEST_BRIDGE_NAME))
                        .build())
                .add(op.mutate(bridge)
                        .addMutation(flood_vlans, Mutator.INSERT, Sets.newHashSet(200,400))
                        .where(name.opEqual(TEST_BRIDGE_NAME))
                        .build())
                .add(op.mutate(bridge)
                        .addMutation(externalIds, Mutator.INSERT, ImmutableMap.of("key2","value2"))
                        .where(name.opEqual(TEST_BRIDGE_NAME))
                        .build())
                .add(op.mutate(ovsTable)
                        .addMutation(bridges, Mutator.INSERT, Sets.newHashSet(new UUID(namedUuid)))
                        .where(_uuid.opEqual(parentTable))
                        .build())
                .add(op.commit(true));

        ListenableFuture<List<OperationResult>> results = transactionBuilder.execute();
        List<OperationResult> operationResults = results.get();
        assertFalse(operationResults.isEmpty());
        // Check if Results matches the number of operations in transaction
        assertEquals(transactionBuilder.getOperations().size(), operationResults.size());
        LOG.info("Insert & Update operation results = {}", operationResults);
        for (OperationResult result : operationResults) {
            assertNull(result.getError());
        }
        testBridgeUuid = operationResults.get(insertOperationIndex).getUuid();
    }

    private void assertTransaction() throws InterruptedException, ExecutionException {
        assertNotNull(dbSchema);
        TableSchema<GenericTableSchema> bridge = dbSchema.table("Bridge", GenericTableSchema.class);
        ColumnSchema<GenericTableSchema, String> name = bridge.column("name", String.class);

        /*
         * Adding a separate Assert operation in a transaction. Lets not mix this with other
         * valid transactions as above.
         */
        ListenableFuture<List<OperationResult>> results = ovs.transactBuilder(dbSchema)
                .add(op.delete(bridge)
                        .where(name.opEqual(TEST_BRIDGE_NAME))
                        .build())
                .add(op.assertion("Assert12345")) // Failing intentionally
                .execute();

        List<OperationResult> operationResults = results.get();
        assertFalse(operationResults.isEmpty());
        /* Testing for an Assertion Error */
        assertFalse(operationResults.get(1).getError() == null);
        LOG.info("Assert operation results = {}", operationResults);
    }

    private void abortTransaction() throws InterruptedException, ExecutionException {
        assertNotNull(dbSchema);
        TableSchema<GenericTableSchema> bridge = dbSchema.table("Bridge", GenericTableSchema.class);
        ColumnSchema<GenericTableSchema, String> name = bridge.column("name", String.class);

        /*
         * Adding a separate Abort operation in a transaction. Lets not mix this with other
         * valid transactions as above.
         */
        ListenableFuture<List<OperationResult>> results = ovs.transactBuilder(dbSchema)
                .add(op.delete(bridge)
                        .where(name.opEqual(TEST_BRIDGE_NAME))
                        .build())
                .add(op.abort())
                .execute();

        List<OperationResult> operationResults = results.get();
        assertFalse(operationResults.isEmpty());
        /* Testing for Abort Error */
        assertFalse(operationResults.get(1).getError() == null);
        LOG.info("Abort operation results = {}", operationResults);
    }

    public void testGetDBs() throws ExecutionException, InterruptedException {
        ListenableFuture<List<String>> databases = ovs.getDatabases();
        List<String> dbNames = databases.get();
        assertNotNull(dbNames);
        boolean hasOpenVswitchSchema = false;
        for(String dbName : dbNames) {
           if (dbName.equals(LibraryIntegrationTestUtils.OPEN_VSWITCH)) {
                hasOpenVswitchSchema = true;
                break;
           }
        }
        assertTrue(LibraryIntegrationTestUtils.OPEN_VSWITCH
                + " schema is not supported by the switch", hasOpenVswitchSchema);
    }

    @Before
    public void setup() throws Exception {
        schema = LibraryIntegrationTestUtils.OPEN_VSWITCH;
        super.setup2();

        if (ovs != null) {
            return;
        }

        ovs = LibraryIntegrationTestUtils.getTestConnection(this);
        assertNotNull("Failed to get connection to ovsdb node", ovs);
        LOG.info("Connection Info: {}", ovs.getConnectionInfo().toString());
        testGetDBs();
        dbSchema = ovs.getSchema(LibraryIntegrationTestUtils.OPEN_VSWITCH).get();
    }

    @After
    public void tearDown() throws InterruptedException, ExecutionException {
        if (dbSchema == null) {
            return;
        }
        TableSchema<GenericTableSchema> bridge = dbSchema.table("Bridge", GenericTableSchema.class);
        ColumnSchema<GenericTableSchema, String> name = bridge.column("name", String.class);
        GenericTableSchema ovsTable = dbSchema.table("Open_vSwitch", GenericTableSchema.class);
        ColumnSchema<GenericTableSchema, Set<UUID>> bridges = ovsTable.multiValuedColumn("bridges", UUID.class);
        ColumnSchema<GenericTableSchema, UUID> _uuid = ovsTable.column("_uuid", UUID.class);
        UUID parentTable = selectOpenVSwitchTableUuid();

        ListenableFuture<List<OperationResult>> results = ovs.transactBuilder(dbSchema)
                .add(op.delete(bridge)
                        .where(name.opEqual(TEST_BRIDGE_NAME))
                        .build())
                .add(op.mutate(ovsTable)
                        .addMutation(bridges, Mutator.DELETE, Sets.newHashSet(testBridgeUuid))
                        .where(_uuid.opEqual(parentTable))
                        .build())
                .add(op.commit(true))
                .execute();

        List<OperationResult> operationResults = results.get();
        LOG.info("Delete operation results = {}", operationResults);
        ovs.disconnect();
    }
}
