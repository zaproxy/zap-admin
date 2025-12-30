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

import java.util.Map;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

/**
 * Task that handles a main release, if any.
 *
 * <p>Sends a repository dispatch to release the main and nightly Docker images.
 */
public abstract class HandleMainRelease extends MainReleaseRepositoryDispatch {

    @Input
    public abstract Property<String> getEventTypeNightly();

    @Input
    public abstract Property<String> getEventTypeWeekly();

    @Input
    public abstract MapProperty<String, Object>  getClientPayloadWeekly();

    @Override
    protected void sendDispatch() {
        super.sendDispatch();
        sendRepositoryDispatch(getEventTypeNightly().get(), Map.of());
        sendRepositoryDispatch(getEventTypeWeekly().get(), getClientPayloadWeekly().get());
    }
}
