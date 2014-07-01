/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.groupbasedpolicy.renderer.ofoverlay;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Manage connected switches and ensure their configuration is set up 
 * correctly
 * @author readams
 */
public class SwitchManager implements AutoCloseable, DataChangeListener {
    private static final Logger LOG = 
            LoggerFactory.getLogger(SwitchManager.class);

    private final DataBroker dataProvider;
    private final static InstanceIdentifier<Nodes> nodesIid =
            InstanceIdentifier.builder(Nodes.class).build();
    private ListenerRegistration<DataChangeListener> nodesReg;

    private ConcurrentHashMap<NodeId, SwitchState> switches = 
            new ConcurrentHashMap<>();
    private List<SwitchListener> listeners = new CopyOnWriteArrayList<>();

    public SwitchManager(DataBroker dataProvider) {
        super();
        this.dataProvider = dataProvider;
        nodesReg = 
                dataProvider.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL, 
                                                        nodesIid, this, 
                                                        DataChangeScope.ONE);
        LOG.debug("Initialized OFOverlay switch manager");
    }

    // *************
    // SwitchManager
    // *************
    
    /**
     * Get the collection of switches that are in the "ready" state.  Note
     * that the collection may be concurrently modified
     * @return A {@link Collection} containing the switches that are ready.
     */
    public Collection<NodeId> getReadySwitches() {
        Collection<SwitchState> ready = 
                Collections2.filter(switches.values(), 
                            new Predicate<SwitchState>() {
                    @Override
                    public boolean apply(SwitchState input) {
                        return SwitchStatus.READY.equals(input.status); 
                    }
                });
        return Collections2.transform(ready, 
                                      new Function<SwitchState, NodeId>() {
            @Override
            public NodeId apply(SwitchState input) {
                return input.switchNode.getId();
            }
        });
    }

    /**
     * Add a {@link SwitchListener} to get notifications of switch events
     * @param listener the {@link SwitchListener} to add
     */
    public void registerListener(SwitchListener listener) {
        listeners.add(listener);
    }
    
    // *************
    // AutoCloseable
    // *************

    @Override
    public void close() throws Exception {
        nodesReg.close();
    }

    // *************
    // DataChangeListener
    // *************

    @Override
    public void onDataChanged(AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> change) {
        readSwitches();
    }
    
    // **************
    // Implementation
    // **************
    
    /**
     * Read the set of switches from the ODL inventory and update our internal
     * map.
     * 
     * <p>This is safe only if there can only be one notification at a time, 
     * as there are race conditions in the face of concurrent data change 
     * notifications
     */
    private void readSwitches() {
        ListenableFuture<Optional<DataObject>> future = 
                dataProvider.newReadOnlyTransaction()
                    .read(LogicalDatastoreType.OPERATIONAL,nodesIid);
        DataObject dao;
        try {
            dao = future.get().get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Count not read switch information", e);
            return;
        }
        
        // Switches are registered as Nodes in the inventory; OpenFlow switches
        // are of type FlowCapableNode
        if (dao instanceof Nodes) {
            HashSet<NodeId> activeSwitches = new HashSet<>(); 
            Nodes nodes = (Nodes)dao;
            for (Node node : nodes.getNode()) {
                FlowCapableNode fcn = node.getAugmentation(FlowCapableNode.class);
                if (fcn != null && fcn.getDescription() != null) {
                    activeSwitches.add(node.getId());
                    SwitchState state = switches.get(node.getId()); 
                    if (state == null) {
                        state = new SwitchState(node);
                        SwitchState old = 
                                switches.putIfAbsent(node.getId(), state);
                        if (old != null) {
                            state = old;
                        } else {
                            // XXX for now just go straight to READY
                            switchReady(node.getId());
                            LOG.info("New switch {} connected", node.getId());
                        }
                    }
                }
            }
            for (NodeId nodeId : switches.keySet()) {
                if (!activeSwitches.contains(nodeId)) {
                    removeSwitch(nodeId);
                }
            }
        }
    }
    
    /**
     * Set the ready state of the node to READY and notify listeners
     */
    private void switchReady(NodeId nodeId) {
        SwitchState state = switches.get(nodeId);
        if (state != null) {
            state.status = SwitchStatus.READY;
            for (SwitchListener listener : listeners) {
                listener.switchReady(nodeId);
            }
        }
    }

    /**
     * Remove the switch from the switches we're keeping track of and
     * notify listeners
     */
    private void removeSwitch(NodeId nodeId) {
        LOG.info("Switch {} removed", nodeId);
        switches.remove(nodeId);
    }
    
    private enum SwitchStatus {
        /**
         * The switch is connected but not yet configured
         */
        PREPARING,
        /**
         * The switch is ready to for policy rules to be installed
         */
        READY
    }
    
    /**
     * Internal representation of the state of a connected switch
     */
    private static class SwitchState {
        Node switchNode;
        SwitchStatus status = SwitchStatus.PREPARING;
        public SwitchState(Node switchNode) {
            super();
            this.switchNode = switchNode;
        }
        
    }

}
