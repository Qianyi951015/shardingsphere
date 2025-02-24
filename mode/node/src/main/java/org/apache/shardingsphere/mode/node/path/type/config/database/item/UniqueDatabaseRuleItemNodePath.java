/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.mode.node.path.type.config.database.item;

import lombok.Getter;
import org.apache.shardingsphere.mode.node.path.engine.searcher.NodePathPattern;
import org.apache.shardingsphere.mode.node.path.engine.searcher.NodePathSearcher;
import org.apache.shardingsphere.mode.node.path.type.metadata.rule.DatabaseRuleItem;
import org.apache.shardingsphere.mode.node.path.type.metadata.rule.DatabaseRuleNodePath;
import org.apache.shardingsphere.mode.node.path.type.version.VersionNodePathParser;

/**
 * Unique database rule item node path.
 */
public final class UniqueDatabaseRuleItemNodePath {
    
    private final String type;
    
    @Getter
    private final VersionNodePathParser versionNodePathParser;
    
    public UniqueDatabaseRuleItemNodePath(final String ruleType, final String type) {
        this.type = type;
        versionNodePathParser = NodePathSearcher.getVersion(new DatabaseRuleNodePath(NodePathPattern.IDENTIFIER, ruleType, new DatabaseRuleItem(type)));
    }
    
    /**
     * Get path.
     *
     * @return path
     */
    public String getPath() {
        return String.join("/", type);
    }
}
