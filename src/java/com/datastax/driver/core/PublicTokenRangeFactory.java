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

package com.datastax.driver.core;

import org.apache.cassandra.utils.ByteBufferUtil;

/**
 * @author Marek Lewandowski <marek.m.lewandowski@gmail.com>
 * @since 18/04/16
 */
public class PublicTokenRangeFactory
{
    public static TokenRange getFullMurmur3TokenRange() {
        Token.Factory factory = Token.getFactory("Murmur3Partitioner");
        return new TokenRange(factory.minToken(), factory.minToken(), factory);
    }

    public static com.datastax.driver.core.Token convert(org.apache.cassandra.dht.Token token) {
        return Token.getFactory("Murmur3Partitioner").hash(ByteBufferUtil.bytes((Long)token.getTokenValue()));
    }
}
