/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.network;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.apache.ignite.internal.util.CompletableFutures.nullCompletedFuture;

import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import org.apache.ignite.internal.manager.IgniteComponent;
import org.apache.ignite.internal.worker.CriticalWorker;
import org.apache.ignite.internal.worker.CriticalWorkerRegistry;
import org.apache.ignite.internal.worker.configuration.CriticalWorkersConfiguration;
import org.jetbrains.annotations.Nullable;

/**
 * This component is responsible for registering Netty workers with the {@link CriticalWorkerRegistry} and for updating their heartbeats.
 */
public class NettyWorkersRegistrar implements IgniteComponent {
    /*
     * It seems impossible to instrument tasks executed by Netty event loops, so the strategy we use is to
     * send 'heartbeat' tasks to each of the event loops periodically; these tasks just update the heartbeat timestamp
     * of the worker corresponding to an event loop. If an event loop's thread is blocked, the hearbeat will not
     * be updated, and the worker watchdog will treat the event loop worker as blocked.
     */

    private final CriticalWorkerRegistry criticalWorkerRegistry;

    private final ScheduledExecutorService scheduler;

    private final NettyBootstrapFactory bootstrapFactory;

    private final CriticalWorkersConfiguration criticalWorkersConfiguration;

    private volatile List<NettyWorker> workers = new CopyOnWriteArrayList<>();

    @Nullable
    private volatile ScheduledFuture<?> sendHearbeatsTaskFuture;

    /**
     * Constructor.
     *
     * @param criticalWorkerRegistry Used to register workers representing Netty event loops to detect when their threads are
     *         blocked.
     * @param scheduler Used to schedule periodic tasks.
     * @param bootstrapFactory Used to obtain Netty workers.
     */
    public NettyWorkersRegistrar(
            CriticalWorkerRegistry criticalWorkerRegistry,
            ScheduledExecutorService scheduler,
            NettyBootstrapFactory bootstrapFactory,
            CriticalWorkersConfiguration criticalWorkersConfiguration
    ) {
        this.criticalWorkerRegistry = criticalWorkerRegistry;
        this.scheduler = scheduler;
        this.bootstrapFactory = bootstrapFactory;
        this.criticalWorkersConfiguration = criticalWorkersConfiguration;
    }

    @Override
    public CompletableFuture<Void> start() {
        List<NettyWorker> nettyWorkers = new ArrayList<>();
        for (EventLoopGroup group : bootstrapFactory.eventLoopGroups()) {
            registerWorkersFor(group, nettyWorkers);
        }
        workers = List.copyOf(nettyWorkers);

        long heartbeatInterval = criticalWorkersConfiguration.nettyThreadsHeartbeatInterval().value();
        sendHearbeatsTaskFuture = scheduler.scheduleAtFixedRate(this::sendHearbeats, heartbeatInterval, heartbeatInterval, MILLISECONDS);

        return nullCompletedFuture();
    }

    private void registerWorkersFor(EventLoopGroup group, List<NettyWorker> nettyWorkers) {
        List<NettyWorker> groupWorkers = new ArrayList<>();

        for (EventExecutor eventExecutor : group) {
            SingleThreadEventExecutor executor = (SingleThreadEventExecutor) eventExecutor;
            groupWorkers.add(new NettyWorker(executor));
        }

        for (NettyWorker worker : groupWorkers) {
            criticalWorkerRegistry.register(worker);
        }

        nettyWorkers.addAll(groupWorkers);
    }

    private void sendHearbeats() {
        for (NettyWorker worker : workers) {
            worker.sendHeartbeat();
        }
    }

    @Override
    public void stop() throws Exception {
        Future<?> heartBeatsTaskFuture = sendHearbeatsTaskFuture;
        if (heartBeatsTaskFuture != null) {
            heartBeatsTaskFuture.cancel(false);
        }

        for (NettyWorker worker : workers) {
            criticalWorkerRegistry.unregister(worker);
        }
    }

    private static class NettyWorker implements CriticalWorker {
        private final SingleThreadEventExecutor eventExecutor;

        private volatile long heartbeatNanos = System.nanoTime();

        private final Runnable sendHeartbeatTask = () -> heartbeatNanos = System.nanoTime();

        private NettyWorker(SingleThreadEventExecutor eventExecutor) {
            this.eventExecutor = eventExecutor;
        }

        @Override
        public long threadId() {
            return eventExecutor.threadProperties().id();
        }

        @Override
        public long heartbeatNanos() {
            return heartbeatNanos;
        }

        private void sendHeartbeat() {
            eventExecutor.execute(sendHeartbeatTask);
        }
    }
}