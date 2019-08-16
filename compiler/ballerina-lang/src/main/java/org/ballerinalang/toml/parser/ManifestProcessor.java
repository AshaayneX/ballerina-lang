/*
 *  Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.ballerinalang.toml.parser;

import com.moandjiezana.toml.Toml;
import org.ballerinalang.compiler.BLangCompilerException;
import org.ballerinalang.toml.exceptions.TomlException;
import org.ballerinalang.toml.model.DependencyMetadata;
import org.ballerinalang.toml.model.Manifest;
import org.wso2.ballerinalang.compiler.FileSystemProjectDirectory;
import org.wso2.ballerinalang.compiler.SourceDirectory;
import org.wso2.ballerinalang.compiler.util.CompilerContext;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manifest Processor which processes the toml file parsed and populate the Manifest POJO.
 *
 * @since 0.964
 */
public class ManifestProcessor {

    private static final CompilerContext.Key<ManifestProcessor> MANIFEST_PROC_KEY = new CompilerContext.Key<>();
    private static final Pattern semVerPattern = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");
    private final Manifest manifest;
    
    public static ManifestProcessor getInstance(CompilerContext context) {
        try {
            ManifestProcessor manifestProcessor = context.get(MANIFEST_PROC_KEY);
            if (manifestProcessor == null) {
                SourceDirectory sourceDirectory = context.get(SourceDirectory.class);
                Manifest manifest = new Manifest();
                if (sourceDirectory instanceof FileSystemProjectDirectory) {
                    manifest = ManifestProcessor.parseTomlContentAsStream(sourceDirectory.getManifestContent());
                }
                ManifestProcessor instance = new ManifestProcessor(manifest);
                context.put(MANIFEST_PROC_KEY, instance);
                return instance;
            }
            return manifestProcessor;
        } catch (TomlException tomlException) {
            throw new BLangCompilerException(tomlException.getMessage());
        }
    }

    private ManifestProcessor(Manifest manifest) {
        this.manifest = manifest;
    }

    public Manifest getManifest() {
        return this.manifest;
    }
    
    /**
     * Get the char stream of the content from file.
     *
     * @param tomlPath path of the toml file
     * @return manifest object
     * @throws IOException   exception if the file cannot be found
     * @throws TomlException exception when parsing toml
     */
    public static Manifest parseTomlContentFromFile(Path tomlPath) throws IOException, TomlException {
        InputStream targetStream = new FileInputStream(tomlPath.toFile());
        Manifest manifest = parseTomlContentAsStream(targetStream);
        validateManifestDependencies(manifest);
        return manifest;
    }
    
    /**
     * Get the char stream from string content.
     *
     * @param content toml file content as a string
     * @return manifest object
     * @throws TomlException exception when parsing toml
     */
    public static Manifest parseTomlContentFromString(String content) throws TomlException {
        try {
            Toml toml = new Toml().read(content);
            if (toml.isEmpty()) {
                throw new TomlException("invalid Ballerina.toml file: organization name and the version of the " +
                                        "project is missing. example: \n" +
                                        "[project]\n" +
                                        "org-name=\"my_org\"\n" +
                                        "version=\"1.0.0\"\n");
            }
            
            if (null == toml.getTable("project")) {
                throw new TomlException("invalid Ballerina.toml file: cannot find [project]");
            }
            
            Manifest manifest = toml.to(Manifest.class);
            manifest.getProject().setOrgName(toml.getString("project.org-name"));
            validateManifestProject(manifest);
            validateManifestDependencies(manifest);
            return manifest;
        } catch (IllegalStateException ise) {
            String tomlErrMsg = lowerCaseFirstLetter(ise.getMessage().toLowerCase(Locale.getDefault()));
            throw new TomlException("invalid Ballerina.toml file: " + tomlErrMsg);
        }
    }
    
    /**
     * Get the char stream from inputstream.
     *
     * @param inputStream inputstream of the toml file content
     * @return manifest object
     * @throws TomlException exception when parsing toml
     */
    public static Manifest parseTomlContentAsStream(InputStream inputStream) throws TomlException {
        try {
            Toml toml = new Toml().read(inputStream);
            if (toml.isEmpty()) {
                throw new TomlException("invalid Ballerina.toml file: organization name and the version of the " +
                                        "project is missing. example: \n" +
                                        "[project]\n" +
                                        "org-name=\"my_org\"\n" +
                                        "version=\"1.0.0\"\n");
            }
    
            if (null == toml.getTable("project")) {
                throw new TomlException("invalid Ballerina.toml file: cannot find [project]");
            }
            
            Manifest manifest = toml.to(Manifest.class);
            manifest.getProject().setOrgName(toml.getString("project.org-name"));
            validateManifestProject(manifest);
            validateManifestDependencies(manifest);
            return manifest;
        } catch (IllegalStateException ise) {
            String tomlErrMsg = lowerCaseFirstLetter(ise.getMessage().toLowerCase(Locale.getDefault()));
            throw new TomlException("invalid Ballerina.toml file: " + tomlErrMsg);
        }
    }
    
    /**
     * Validate the project block in manifest.
     *
     * @param manifest The manifest.
     * @throws TomlException When the project block is invalid.
     */
    private static void validateManifestProject(Manifest manifest) throws TomlException {
        if (null == manifest.getProject().getOrgName() || "".equals(manifest.getProject().getOrgName())) {
            throw new TomlException("invalid Ballerina.toml file: cannot find 'org-name' under [project]");
        }
        
        if (null == manifest.getProject().getVersion() || "".equals(manifest.getProject().getVersion())) {
            throw new TomlException("invalid Ballerina.toml file: cannot find 'version' under [project]");
        }
    
        Matcher semverMatcher = semVerPattern.matcher(manifest.getProject().getVersion());
        if (!semverMatcher.matches()) {
            throw new TomlException("invalid Ballerina.toml file: 'version' under [project] is not semver");
        }
    }
    
    /**
     * Validate dependencies block in manifest.
     *
     * @param manifest The manifest.
     * @throws TomlException When the dependencies block is invalid.
     */
    private static void validateManifestDependencies(Manifest manifest) throws TomlException {
        for (Map.Entry<String, Object> dependency : manifest.getDependenciesAsObjectMap().entrySet()) {
            DependencyMetadata metadata = new DependencyMetadata();
            if (dependency.getValue() instanceof String) {
                metadata.setVersion((String) dependency.getValue());
            } else if (dependency.getValue() instanceof Map) {
                Map metadataMap = (Map) dependency.getValue();
                if (metadataMap.keySet().contains("version") && metadataMap.get("version") instanceof String) {
                    metadata.setVersion((String) metadataMap.get("version"));
                }
    
                if (metadataMap.keySet().contains("path") &&  metadataMap.get("path") instanceof String) {
                    metadata.setPath((String) metadataMap.get("path"));
        
                    Path dependencyBaloPath = Paths.get(metadata.getPath());
                    if (!Files.exists(dependencyBaloPath)) {
                        throw new TomlException("invalid Ballerina.toml file: balo file for dependency [" +
                                                         dependency.getKey() + "] does not exists: " +
                                                         dependencyBaloPath.toAbsolutePath());
                    }
                }
            } else {
                throw new TomlException("invalid Ballerina.toml file: invalid metadata found for dependency" +
                                                 " [" + dependency.getKey() + "]");
            }
        }
    }
    
    /**
     * Lowercase the first letter of this string.
     *
     * @param content contect
     * @return converted string
     */
    private static String lowerCaseFirstLetter(String content) {
        return content.substring(0, 1).toLowerCase(Locale.getDefault()) + content.substring(1);
    }
}