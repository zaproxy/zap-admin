/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2021 The ZAP Development Team
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

import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.options.Option;

/** An abstract task that allows to update {@code ZapVersions.xml} files. */
public abstract class AbstractUpdateZapVersionsEntries extends DefaultTask
        implements UpdateZapVersionsEntries {

    public AbstractUpdateZapVersionsEntries() {
        setGroup("ZAP");
    }

    @Option(option = "into", description = "The ZapVersions.xml files to update.")
    public void setFiles(List<String> files) {
        getInto().setFrom(files);
    }
}
