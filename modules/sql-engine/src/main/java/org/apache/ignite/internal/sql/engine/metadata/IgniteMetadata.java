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

package org.apache.ignite.internal.sql.engine.metadata;

import java.util.List;
import org.apache.calcite.rel.metadata.ChainedRelMetadataProvider;
import org.apache.calcite.rel.metadata.DefaultRelMetadataProvider;
import org.apache.calcite.rel.metadata.RelMetadataProvider;

/**
 * Utility class, holding metadata related interfaces and metadata providers.
 */
public class IgniteMetadata {
    public static final RelMetadataProvider METADATA_PROVIDER =
            ChainedRelMetadataProvider.of(
                    List.of(
                            // Ignite overriden providers
                            IgniteMdDistribution.SOURCE,
                            IgniteMdPercentageOriginalRows.SOURCE,
                            IgniteMdCumulativeCost.SOURCE,
                            IgniteMdNonCumulativeCost.SOURCE,
                            IgniteMdRowCount.SOURCE,
                            IgniteMdPredicates.SOURCE,
                            IgniteMdCollation.SOURCE,
                            IgniteMdSelectivity.SOURCE,
                            IgniteMdDistinctRowCount.SOURCE,
                            IgniteMdColumnOrigins.SOURCE,

                            // Basic providers
                            DefaultRelMetadataProvider.INSTANCE
                    )
            );
}
