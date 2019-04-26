/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2018 The ZAP Development Team
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

import java.io.InputStream;
import java.io.Reader;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;

class CustomXmlConfiguration extends XMLConfiguration {

    private static final long serialVersionUID = 7018390148134058207L;

    public CustomXmlConfiguration() {
        setEncoding("UTF-8");
        setDelimiterParsingDisabled(true);
        setRootElementName("ZAP");
    }

    @Override
    protected Transformer createTransformer() throws TransformerException {
        Transformer transformer = super.createTransformer();
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        return transformer;
    }

    @Override
    public void load(InputStream in) throws ConfigurationException {
        super.load(in);
        postLoad();
    }

    @Override
    public void load(Reader in) throws ConfigurationException {
        super.load(in);
        postLoad();
    }

    private void postLoad() {
        // Ensure it's used a "clean" document for proper indentation of the configurations.
        // In newer Java versions (9+) the text nodes are indented as well, which would lead
        // to additional text nodes each time the configuration is loaded/saved.
        clearReferences(getRootNode());
        String rootName = getRootElementName();
        getDocument().removeChild(getDocument().getDocumentElement());
        getDocument().appendChild(getDocument().createElement(rootName));
    }
}
