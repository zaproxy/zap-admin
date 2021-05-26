/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2020 The ZAP Development Team
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
package org.zaproxy.gradle;

import java.util.Collections;
import java.util.List;
import org.apache.commons.configuration.XMLConfiguration;
import org.zaproxy.gradle.ReleaseData.ReleaseFile;

/** A task that generates the weekly release data for the website. */
public abstract class GenerateWebsiteWeeklyReleaseData extends AbstractGenerateWebsiteReleaseData {

    private static final String DAILY_ELEMENT = "core.daily";
    private static final String DAILY_SIZE_ELEMENT = DAILY_ELEMENT + ".size";
    private static final String DAILY_URL_ELEMENT = DAILY_ELEMENT + ".url";

    public GenerateWebsiteWeeklyReleaseData() {
        super("weekly");
    }

    @Override
    protected List<ReleaseFile> createReleaseFiles(XMLConfiguration zapVersionsXml) {
        return Collections.singletonList(
                new ReleaseFile(
                        "Weekly Cross Platform Package",
                        "cp-p",
                        toMegaBytes(zapVersionsXml.getString(DAILY_SIZE_ELEMENT)),
                        zapVersionsXml.getString(DAILY_URL_ELEMENT)));
    }
}
