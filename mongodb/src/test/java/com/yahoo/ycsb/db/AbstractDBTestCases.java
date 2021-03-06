/*                                                                                                                                                                                
 * Copyright (c) 2014, Yahoo!, Inc. All rights reserved.                                                                                                                             
 *                                                                                                                                                                                 
 * Licensed under the Apache License, Version 2.0 (the "License"); you                                                                                                             
 * may not use this file except in compliance with the License. You                                                                                                                
 * may obtain a copy of the License at                                                                                                                                             
 *                                                                                                                                                                                 
 * http://www.apache.org/licenses/LICENSE-2.0                                                                                                                                      
 *                                                                                                                                                                                 
 * Unless required by applicable law or agreed to in writing, software                                                                                                             
 * distributed under the License is distributed on an "AS IS" BASIS,                                                                                                               
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or                                                                                                                 
 * implied. See the License for the specific language governing                                                                                                                    
 * permissions and limitations under the License. See accompanying                                                                                                                 
 * LICENSE file.                                                                                                                                                                   
 */
package com.yahoo.ycsb.db;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNoException;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.Vector;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.yahoo.ycsb.ByteArrayByteIterator;
import com.yahoo.ycsb.ByteIterator;
import com.yahoo.ycsb.DB;

import de.flapdoodle.embed.mongo.Command;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.ArtifactStoreBuilder;
import de.flapdoodle.embed.mongo.config.IMongodConfig;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.config.RuntimeConfigBuilder;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.io.directories.FixedPath;
import de.flapdoodle.embed.process.runtime.Network;

/**
 * MongoDbClientTest provides runs the basic DB test cases.
 */
@SuppressWarnings("boxing")
public abstract class AbstractDBTestCases {

    /** The running Mongodb process. */
    private static MongodProcess ourMongod = null;

    /** The handle to the running server. */
    private static MongodExecutable ourMongodExecutable = null;

    /** The directory to download the MongoDB executables to. */
    private static final File TMP_DIR = new File("target/mongodb");

    /**
     * Start a test mongd instance.
     */
    @BeforeClass
    public static void setUpBeforeClass() {
        TMP_DIR.mkdirs();

        MongodStarter starter = MongodStarter
                .getInstance(new RuntimeConfigBuilder()
                        .defaults(Command.MongoD)
                        .artifactStore(
                                new ArtifactStoreBuilder()
                                        .defaults(Command.MongoD)
                                        .useCache(false)
                                        .tempDir(
                                                new FixedPath(TMP_DIR
                                                        .getAbsolutePath())))
                        .build());
        int port = 27017;

        try {
            IMongodConfig mongodConfig = new MongodConfigBuilder()
                    .version(Version.Main.PRODUCTION)
                    .net(new Net(port, Network.localhostIsIPv6())).build();

            ourMongodExecutable = starter.prepare(mongodConfig);
            ourMongod = ourMongodExecutable.start();
        }
        catch (IOException error) {
            assumeNoException(error);
        }
    }

    /**
     * Stops the test server.
     */
    @AfterClass
    public static void tearDownAfterClass() {
        if (ourMongod != null) {
            ourMongod.stop();
            ourMongod = null;
        }
        if (ourMongodExecutable != null) {
            ourMongodExecutable.stop();
            ourMongodExecutable = null;
        }
    }

    /**
     * Test method for {@link DB#insert}, {@link DB#read}, and {@link DB#delete}
     * .
     */
    @Test
    public void testInsertReadDelete() {
        final DB client = getDB();

        final String table = "test";
        final String id = "delete";

        HashMap<String, ByteIterator> inserted = new HashMap<String, ByteIterator>();
        inserted.put("a", new ByteArrayByteIterator(new byte[] { 1, 2, 3, 4 }));
        int result = client.insert(table, id, inserted);
        assertThat("Insert did not return success (0).", result, is(0));

        HashMap<String, ByteIterator> read = new HashMap<String, ByteIterator>();
        Set<String> keys = Collections.singleton("a");
        result = client.read(table, id, keys, read);
        assertThat("Read did not return success (0).", result, is(0));
        for (String key : keys) {
            ByteIterator iter = read.get(key);

            assertThat("Did not read the inserted field: " + key, iter,
                    notNullValue());
            assertTrue(iter.hasNext());
            assertThat(iter.nextByte(), is(Byte.valueOf((byte) 1)));
            assertTrue(iter.hasNext());
            assertThat(iter.nextByte(), is(Byte.valueOf((byte) 2)));
            assertTrue(iter.hasNext());
            assertThat(iter.nextByte(), is(Byte.valueOf((byte) 3)));
            assertTrue(iter.hasNext());
            assertThat(iter.nextByte(), is(Byte.valueOf((byte) 4)));
            assertFalse(iter.hasNext());
        }

        result = client.delete(table, id);
        assertThat("Delete did not return success (0).", result, is(0));

        read.clear();
        result = client.read(table, id, null, read);
        assertThat("Read, after delete, did not return not found (1).", result,
                is(1));
        assertThat("Found the deleted fields.", read.size(), is(0));

        result = client.delete(table, id);
        assertThat("Delete did not return not found (1).", result, is(1));
    }

    /**
     * Test method for {@link DB#insert}, {@link DB#read}, and {@link DB#update}
     * .
     */
    @Test
    public void testInsertReadUpdate() {
        DB client = getDB();

        final String table = "test";
        final String id = "update";

        HashMap<String, ByteIterator> inserted = new HashMap<String, ByteIterator>();
        inserted.put("a", new ByteArrayByteIterator(new byte[] { 1, 2, 3, 4 }));
        int result = client.insert(table, id, inserted);
        assertThat("Insert did not return success (0).", result, is(0));

        HashMap<String, ByteIterator> read = new HashMap<String, ByteIterator>();
        Set<String> keys = Collections.singleton("a");
        result = client.read(table, id, keys, read);
        assertThat("Read did not return success (0).", result, is(0));
        for (String key : keys) {
            ByteIterator iter = read.get(key);

            assertThat("Did not read the inserted field: " + key, iter,
                    notNullValue());
            assertTrue(iter.hasNext());
            assertThat(iter.nextByte(), is(Byte.valueOf((byte) 1)));
            assertTrue(iter.hasNext());
            assertThat(iter.nextByte(), is(Byte.valueOf((byte) 2)));
            assertTrue(iter.hasNext());
            assertThat(iter.nextByte(), is(Byte.valueOf((byte) 3)));
            assertTrue(iter.hasNext());
            assertThat(iter.nextByte(), is(Byte.valueOf((byte) 4)));
            assertFalse(iter.hasNext());
        }

        HashMap<String, ByteIterator> updated = new HashMap<String, ByteIterator>();
        updated.put("a", new ByteArrayByteIterator(new byte[] { 5, 6, 7, 8 }));
        result = client.update(table, id, updated);
        assertThat("Update did not return success (0).", result, is(0));

        read.clear();
        result = client.read(table, id, null, read);
        assertThat("Read, after update, did not return success (0).", result,
                is(0));
        for (String key : keys) {
            ByteIterator iter = read.get(key);

            assertThat("Did not read the inserted field: " + key, iter,
                    notNullValue());
            assertTrue(iter.hasNext());
            assertThat(iter.nextByte(), is(Byte.valueOf((byte) 5)));
            assertTrue(iter.hasNext());
            assertThat(iter.nextByte(), is(Byte.valueOf((byte) 6)));
            assertTrue(iter.hasNext());
            assertThat(iter.nextByte(), is(Byte.valueOf((byte) 7)));
            assertTrue(iter.hasNext());
            assertThat(iter.nextByte(), is(Byte.valueOf((byte) 8)));
            assertFalse(iter.hasNext());
        }
    }

    /**
     * Test method for {@link DB#scan}.
     */
    @Test
    public void testScan() {
        final DB client = getDB();

        final String table = "test";

        // Insert a bunch of documents.
        for (int i = 0; i < 100; ++i) {
            HashMap<String, ByteIterator> inserted = new HashMap<String, ByteIterator>();
            inserted.put("a", new ByteArrayByteIterator(new byte[] {
                    (byte) (i & 0xFF), (byte) (i >> 8 & 0xFF),
                    (byte) (i >> 16 & 0xFF), (byte) (i >> 24 & 0xFF) }));
            int result = client.insert(table, padded(i), inserted);
            assertThat("Insert did not return success (0).", result, is(0));
        }

        Set<String> keys = Collections.singleton("a");
        Vector<HashMap<String, ByteIterator>> results = new Vector<HashMap<String, ByteIterator>>();
        int result = client.scan(table, "00050", 5, null, results);
        assertThat("Read did not return success (0).", result, is(0));
        assertThat(results.size(), is(5));
        for (int i = 0; i < 5; ++i) {
            HashMap<String, ByteIterator> read = results.get(i);
            for (String key : keys) {
                ByteIterator iter = read.get(key);

                assertThat("Did not read the inserted field: " + key, iter,
                        notNullValue());
                assertTrue(iter.hasNext());
                assertThat(iter.nextByte(),
                        is(Byte.valueOf((byte) ((i + 50) & 0xFF))));
                assertTrue(iter.hasNext());
                assertThat(iter.nextByte(),
                        is(Byte.valueOf((byte) ((i + 50) >> 8 & 0xFF))));
                assertTrue(iter.hasNext());
                assertThat(iter.nextByte(),
                        is(Byte.valueOf((byte) ((i + 50) >> 16 & 0xFF))));
                assertTrue(iter.hasNext());
                assertThat(iter.nextByte(),
                        is(Byte.valueOf((byte) ((i + 50) >> 24 & 0xFF))));
                assertFalse(iter.hasNext());
            }
        }
    }

    /**
     * Gets the test DB.
     * 
     * @return The test DB.
     */
    protected abstract DB getDB();

    /**
     * Creates a zero padded integer.
     * 
     * @param i
     *            The integer to padd.
     * @return The padded integer.
     */
    private String padded(int i) {
        String result = String.valueOf(i);
        while (result.length() < 5) {
            result = "0" + result;
        }
        return result;
    }

}