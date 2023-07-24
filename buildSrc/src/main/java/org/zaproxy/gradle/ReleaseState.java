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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.io.File;
import java.io.IOException;
import java.util.List;

/** The release state computed from {@code ZapVersions.xml} files. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(value = Include.NON_NULL)
public class ReleaseState {

    @JsonProperty private VersionChange mainRelease;
    @JsonProperty private VersionChange weeklyRelease;

    @JsonProperty private List<AddOnChange> addOns;

    public ReleaseState() {}

    public VersionChange getMainRelease() {
        return mainRelease;
    }

    public void setMainRelease(VersionChange mainRelease) {
        this.mainRelease = mainRelease;
    }

    public VersionChange getWeeklyRelease() {
        return weeklyRelease;
    }

    public void setWeeklyRelease(VersionChange weeklyRelease) {
        this.weeklyRelease = weeklyRelease;
    }

    public void setAddOns(List<AddOnChange> addOns) {
        this.addOns = addOns;
    }

    public List<AddOnChange> getAddOns() {
        return addOns;
    }

    /**
     * Writes this {@code ReleaseState} to the given file.
     *
     * @param file the file to write the release state.
     * @throws TaskException if an error occurred while writing the release state.
     */
    public void write(File file) {
        try {
            new ObjectMapper().writeValue(file, this);
        } catch (IOException e) {
            throw new TaskException("Failed to write the release state: " + e.getMessage(), e);
        }
    }

    /**
     * Reads a {@code ReleaseState} from the given file.
     *
     * @param file the file with the release state.
     * @return a new {@code ReleaseState} with the contents from the file.
     * @throws TaskException if an error occurred while reading the release state.
     */
    public static ReleaseState read(File file) {
        try {
            return new ObjectMapper().readValue(file, ReleaseState.class);
        } catch (IOException e) {
            throw new TaskException("Failed to read the release state: " + e.getMessage(), e);
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(value = Include.NON_EMPTY)
    public static class VersionChange {

        @JsonProperty private String previousVersion;

        @JsonProperty private String currentVersion;

        @JsonProperty private boolean newVersion;

        public VersionChange() {}

        public VersionChange(String previousVersion, String currentVersion) {
            this.previousVersion = previousVersion;
            this.currentVersion = currentVersion;
            this.newVersion = previousVersion == null || !previousVersion.equals(currentVersion);
        }

        public boolean isNewVersion() {
            return newVersion;
        }

        public String getPreviousVersion() {
            return previousVersion;
        }

        public void setPreviousVersion(String previousVersion) {
            this.previousVersion = previousVersion;
        }

        public String getCurrentVersion() {
            return currentVersion;
        }

        public void setCurrentVersion(String currentVersion) {
            this.currentVersion = currentVersion;
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(value = Include.NON_EMPTY)
    public static class AddOnChange extends VersionChange {

        @JsonProperty private String id;

        public AddOnChange() {}

        public AddOnChange(String id, String previousVersion, String currentVersion) {
            super(previousVersion, currentVersion);
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }
}
