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

package org.apache.shardingsphere.single.metadata.nodepath;

import org.apache.shardingsphere.infra.spi.type.typed.TypedSPILoader;
import org.apache.shardingsphere.mode.node.path.type.config.database.DatabaseRuleNodePath;
import org.apache.shardingsphere.mode.node.spi.DatabaseRuleNodePathProvider;
import org.apache.shardingsphere.single.config.SingleRuleConfiguration;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleRuleNodePathProviderTest {
    
    private final DatabaseRuleNodePathProvider pathProvider = TypedSPILoader.getService(DatabaseRuleNodePathProvider.class, SingleRuleConfiguration.class);
    
    @Test
    void assertGetDatabaseRuleNodePath() {
        DatabaseRuleNodePath actual = pathProvider.getDatabaseRuleNodePath();
        assertTrue(actual.getNamedItems().isEmpty());
        assertThat(actual.getUniqueItems().size(), is(2));
        assertTrue(actual.getUniqueItems().containsKey(SingleRuleNodePathProvider.TABLES));
        assertTrue(actual.getUniqueItems().containsKey(SingleRuleNodePathProvider.DEFAULT_DATA_SOURCE));
        assertThat(actual.getRuleType(), is(SingleRuleNodePathProvider.RULE_TYPE));
    }
}
