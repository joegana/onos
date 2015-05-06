/*
 * Copyright 2014 Open Networking Laboratory
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
package org.onosproject.store.flow.impl;

import static com.google.common.base.Preconditions.checkState;
import static org.junit.Assert.*;

import java.util.Collections;
import java.util.Map;
import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.onosproject.cluster.NodeId;
import org.onosproject.cluster.RoleInfo;
import org.onosproject.event.AbstractListenerRegistry;
import org.onosproject.event.DefaultEventSinkRegistry;
import org.onosproject.event.Event;
import org.onosproject.event.EventDeliveryService;
import org.onosproject.event.EventSink;
import org.onosproject.mastership.MastershipEvent;
import org.onosproject.mastership.MastershipEvent.Type;
import org.onosproject.mastership.MastershipListener;
import org.onosproject.mastership.MastershipService;
import org.onosproject.mastership.MastershipServiceAdapter;
import org.onosproject.net.DeviceId;
import org.onosproject.store.flow.ReplicaInfo;
import org.onosproject.store.flow.ReplicaInfoEvent;
import org.onosproject.store.flow.ReplicaInfoEventListener;
import org.onosproject.store.flow.ReplicaInfoService;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;

public class ReplicaInfoManagerTest {


    private static final DeviceId DID1 = DeviceId.deviceId("of:1");
    private static final DeviceId DID2 = DeviceId.deviceId("of:2");
    private static final NodeId NID1 = new NodeId("foo");

    private ReplicaInfoManager mgr;
    private ReplicaInfoService service;

    private AbstractListenerRegistry<MastershipEvent, MastershipListener>
        mastershipListenerRegistry;
    private TestEventDispatcher eventDispatcher;


    @Before
    public void setUp() throws Exception {
        mastershipListenerRegistry = new AbstractListenerRegistry<>();

        mgr = new ReplicaInfoManager();
        service = mgr;

        eventDispatcher = new TestEventDispatcher();
        mgr.eventDispatcher = eventDispatcher;
        mgr.mastershipService = new TestMastershipService();

        // register dummy mastership event source
        mgr.eventDispatcher.addSink(MastershipEvent.class, mastershipListenerRegistry);

        mgr.activate();
    }

    @After
    public void tearDown() throws Exception {
        mgr.deactivate();
    }

    @Test
    public void testGetReplicaInfoFor() {
        ReplicaInfo info1 = service.getReplicaInfoFor(DID1);
        assertEquals(Optional.of(NID1), info1.master());
        // backups are always empty for now
        assertEquals(Collections.emptyList(), info1.backups());

        ReplicaInfo info2 = service.getReplicaInfoFor(DID2);
        assertEquals("There's no master", Optional.absent(), info2.master());
        // backups are always empty for now
        assertEquals(Collections.emptyList(), info2.backups());
    }

    @Test
    public void testReplicaInfoEvent() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        service.addListener(new MasterNodeCheck(latch, DID1, NID1));

        // fake MastershipEvent
        eventDispatcher.post(new MastershipEvent(Type.MASTER_CHANGED, DID1,
                new RoleInfo(NID1, new LinkedList<NodeId>())));

        assertTrue(latch.await(1, TimeUnit.SECONDS));
    }


    private final class MasterNodeCheck implements ReplicaInfoEventListener {
        private final CountDownLatch latch;
        private Optional<NodeId> expectedMaster;
        private DeviceId expectedDevice;


        MasterNodeCheck(CountDownLatch latch, DeviceId did,
                NodeId nid) {
            this.latch = latch;
            this.expectedMaster = Optional.fromNullable(nid);
            this.expectedDevice = did;
        }

        @Override
        public void event(ReplicaInfoEvent event) {
            assertEquals(expectedDevice, event.subject());
            assertEquals(expectedMaster, event.replicaInfo().master());
            // backups are always empty for now
            assertEquals(Collections.emptyList(), event.replicaInfo().backups());
            latch.countDown();
        }
    }


    private final class TestMastershipService
            extends MastershipServiceAdapter
            implements MastershipService {

        private Map<DeviceId, NodeId> masters;

        TestMastershipService() {
            masters = Maps.newHashMap();
            masters.put(DID1, NID1);
            // DID2 has no master
        }

        @Override
        public NodeId getMasterFor(DeviceId deviceId) {
            return masters.get(deviceId);
        }

        @Override
        public RoleInfo getNodesFor(DeviceId deviceId) {
            return new RoleInfo(masters.get(deviceId), Collections.emptyList());
        }

        @Override
        public void addListener(MastershipListener listener) {
            mastershipListenerRegistry.addListener(listener);
        }

        @Override
        public void removeListener(MastershipListener listener) {
            mastershipListenerRegistry.removeListener(listener);
        }
    }


    // code clone
    /**
     * Implements event delivery system that delivers events synchronously, or
     * in-line with the post method invocation.
     */
    private static class TestEventDispatcher extends DefaultEventSinkRegistry
            implements EventDeliveryService {

        @SuppressWarnings({ "rawtypes", "unchecked" })
        @Override
        public void post(Event event) {
            EventSink sink = getSink(event.getClass());
            checkState(sink != null, "No sink for event %s", event);
            sink.process(event);
        }
    }
}
