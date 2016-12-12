/*
 * Copyright (c) 2008-2016, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.internal.cluster.impl.operations;

import com.hazelcast.internal.cluster.MemberInfo;
import com.hazelcast.internal.cluster.impl.ClusterDataSerializerHook;
import com.hazelcast.internal.cluster.impl.ClusterServiceImpl;
import com.hazelcast.logging.ILogger;
import com.hazelcast.nio.Address;
import com.hazelcast.nio.Connection;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
import com.hazelcast.spi.impl.NodeEngineImpl;
import com.hazelcast.util.Clock;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

public class MemberInfoUpdateOperation extends AbstractClusterOperation implements JoinOperation, IdentifiedDataSerializable {

    protected Collection<MemberInfo> memberInfos;
    protected long masterTime = Clock.currentTimeMillis();
    protected boolean sendResponse;

    public MemberInfoUpdateOperation() {
        memberInfos = new ArrayList<MemberInfo>();
    }

    public MemberInfoUpdateOperation(Collection<MemberInfo> memberInfos, long masterTime, boolean sendResponse) {
        this.masterTime = masterTime;
        this.memberInfos = memberInfos;
        this.sendResponse = sendResponse;
    }

    @Override
    public void run() throws Exception {
        final ClusterServiceImpl clusterService = getService();
        final NodeEngineImpl nodeEngine = clusterService.getNodeEngine();

        if (!nodeEngine.getNode().joined()) {
            ILogger logger = getLogger();
            if (logger.isFineEnabled()) {
                logger.fine("Ignoring member info update since not joined yet...");
            }

            return;
        }

        if (!checkValid()) {
            return;
        }

        processMemberUpdate();
    }

    protected final void processMemberUpdate() {
        ClusterServiceImpl clusterService = getService();
        clusterService.updateMembers(memberInfos);
    }

    protected final boolean checkValid() {
        final ClusterServiceImpl clusterService = getService();
        final Connection conn = getConnection();

        boolean isLocal = conn == null;

        if (isLocal) {
            return true;
        }

        Address endpoint = conn.getEndPoint();
        Address masterAddress = clusterService.getMasterAddress();
        boolean valid = (endpoint != null && endpoint.equals(masterAddress));
        if (!valid) {
            ILogger logger = getLogger();
            if (logger.isFineEnabled()) {
                logger.fine("Ignoring operation because sender: " + endpoint + " is not known master: " + masterAddress);
            }
        }

        return valid;
    }

    @Override
    public final boolean returnsResponse() {
        return sendResponse;
    }

    @Override
    protected void readInternal(ObjectDataInput in) throws IOException {
        masterTime = in.readLong();
        int size = in.readInt();
        memberInfos = new ArrayList<MemberInfo>(size);
        while (size-- > 0) {
            MemberInfo memberInfo = new MemberInfo();
            memberInfo.readData(in);
            memberInfos.add(memberInfo);
        }
        sendResponse = in.readBoolean();
    }

    @Override
    protected void writeInternal(ObjectDataOutput out) throws IOException {
        out.writeLong(masterTime);
        out.writeInt(memberInfos.size());
        for (MemberInfo memberInfo : memberInfos) {
            memberInfo.writeData(out);
        }
        out.writeBoolean(sendResponse);
    }

    @Override
    protected void toString(StringBuilder sb) {
        super.toString(sb);

        sb.append(", members=");
        for (MemberInfo address : memberInfos) {
            sb.append(address).append(' ');
        }
    }

    public int getFactoryId() {
        return ClusterDataSerializerHook.F_ID;
    }

    @Override
    public int getId() {
        return ClusterDataSerializerHook.MEMBER_INFO_UPDATE;
    }

}

