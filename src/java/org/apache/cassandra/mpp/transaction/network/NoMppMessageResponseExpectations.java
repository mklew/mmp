/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.cassandra.mpp.transaction.network;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * @author Marek Lewandowski <marek.m.lewandowski@gmail.com>
 * @since 06/12/15
 */
public class NoMppMessageResponseExpectations implements MppMessageResponseExpectations<Void>
{
    @Override
    public boolean expectsResponse()
    {
        return false;
    }

    private static class NoMessageResponseDataHolder implements MppMessageResponseDataHolder<Void> {

        public CompletableFuture<Void> getFuture()
        {
            final CompletableFuture<Void> f = new CompletableFuture<>();
            f.complete(null);
            return f;
        }
    }

    public MppMessageResponseDataHolder<Void> createDataHolder(MppMessage message, Collection<MppNetworkService.MessageReceipient> receipients)
    {
        return new NoMessageResponseDataHolder();
    }

    public boolean maybeCompleteResponse(MppMessageResponseDataHolder dataHolder, MppMessage incomingMessage, MppNetworkService.MessageReceipient from)
    {
        throw new IllegalStateException("[initializeDataHolder] No response expected, but dataHolder" + dataHolder
                                        + " message " + incomingMessage + " from " +from);
    }

}
