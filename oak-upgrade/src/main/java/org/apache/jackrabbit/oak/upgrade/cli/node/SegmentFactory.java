/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.oak.upgrade.cli.node;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

import org.apache.jackrabbit.oak.plugins.segment.SegmentNodeBuilder;
import org.apache.jackrabbit.oak.plugins.segment.SegmentNodeState;
import org.apache.jackrabbit.oak.plugins.segment.SegmentNodeStore;
import org.apache.jackrabbit.oak.plugins.segment.file.FileStore;
import org.apache.jackrabbit.oak.plugins.segment.file.FileStore.Builder;
import org.apache.jackrabbit.oak.spi.blob.BlobStore;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.jackrabbit.oak.spi.state.NodeStore;

import com.google.common.io.Closer;

import static com.google.common.base.Preconditions.checkArgument;

public class SegmentFactory implements NodeStoreFactory {

    private final File dir;

    private final boolean mmap;

    public SegmentFactory(String directory, boolean mmap) {
        this.dir = new File(directory);
        this.mmap = mmap;
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException("Not a directory: " + dir.getPath());
        }
    }

    @Override
    public NodeStore create(BlobStore blobStore, Closer closer) throws IOException {
        Builder builder = FileStore.builder(new File(dir, "segmentstore"));
        if (blobStore != null) {
            builder.withBlobStore(blobStore);
        }
        builder.withMaxFileSize(256).withMemoryMapping(mmap);
        final FileStore fs = builder.build();
        closer.register(asCloseable(fs));
        return new TarNodeStore(SegmentNodeStore.builder(fs).build(), new TarNodeStore.SuperRootProvider() {
            @Override
            public void setSuperRoot(NodeBuilder builder) {
                checkArgument(builder instanceof SegmentNodeBuilder);
                SegmentNodeBuilder segmentBuilder = (SegmentNodeBuilder) builder;
                SegmentNodeState lastRoot = (SegmentNodeState) getSuperRoot();

                if (!lastRoot.getRecordId().equals(((SegmentNodeState) segmentBuilder.getBaseState()).getRecordId())) {
                    throw new IllegalArgumentException("The new head is out of date");
                }

                fs.setHead(lastRoot, ((SegmentNodeBuilder) builder).getNodeState());
            }

            @Override
            public NodeState getSuperRoot() {
                return fs.getHead();
            }
        });
    }

    public File getRepositoryDir() {
        return dir;
    }

    private static Closeable asCloseable(final FileStore fs) {
        return new Closeable() {
            @Override
            public void close() throws IOException {
                fs.close();
            }
        };
    }

    @Override
    public String toString() {
        return String.format("SegmentNodeStore[%s]", dir);
    }
}