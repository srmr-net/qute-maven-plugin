package net.srmr;

import io.quarkus.qute.Engine;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Map;

/**
 * Generates files using Quarkus Qute templates.
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public class QuteMojo extends AbstractMojo {

    /**
     * The output directory where the generated files will be placed.
     */
    @Parameter(defaultValue = "${project.build.directory}/generated-sources/qute", property = "outputDir", required = true)
    private File outputDirectory;

    /**
     * File encoding to use when reading templates and writing generated files.
     */
    @Parameter(defaultValue = "${project.build.sourceEncoding}")
    private String encoding;

    /**
     * An optional file extension to strip from the generated file. For example, if removeExtension
     * is ".qute", then a template named "config.xml.qute" will be output as "config.xml".
     */
    @Parameter
    private String removeExtension;

    /**
     * Defines the templates to process. 
     * You must specify the `directory`. You can optionally specify `includes` and `excludes`.
     */
    @Parameter(required = true)
    private TemplateFiles templateFiles;

    /**
     * Properties to pass to the Qute engine as top-level data.
     */
    @Parameter
    private Map<String, String> templateValues;

    /**
     * The current Maven project.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Whether to use Maven properties as template values.
     */
    @Parameter(defaultValue = "true", property = "useMavenProperties")
    private boolean useMavenProperties;

    public static class TemplateFiles {
        public File directory;
        public String[] includes;
        public String[] excludes;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (encoding == null || encoding.isEmpty()) {
            encoding = "UTF-8";
        }
        
        Charset charset = Charset.forName(encoding);

        if (templateFiles == null || templateFiles.directory == null) {
            throw new MojoExecutionException("templateFiles configuration is missing or directory is not specified");
        }

        File basedir = templateFiles.directory;
        if (!basedir.exists()) {
            getLog().info("Template directory " + basedir.getAbsolutePath() + " does not exist. Skipping.");
            return;
        }

        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(basedir);
        if (templateFiles.includes != null && templateFiles.includes.length > 0) {
            scanner.setIncludes(templateFiles.includes);
        }
        if (templateFiles.excludes != null && templateFiles.excludes.length > 0) {
            scanner.setExcludes(templateFiles.excludes);
        }
        scanner.scan();

        String[] includedFiles = scanner.getIncludedFiles();
        if (includedFiles == null || includedFiles.length == 0) {
            getLog().info("No templates found in " + basedir.getAbsolutePath());
            return;
        }

        // Initialize Qute Engine
        Engine engine = Engine.builder().addDefaults().build();

        for (String file : includedFiles) {
            try {
                processTemplate(engine, basedir, file, charset);
            } catch (Exception e) {
                throw new MojoExecutionException("Failed to process template: " + file, e);
            }
        }
    }

    private void processTemplate(Engine engine, File basedir, String relativePath, Charset charset) throws IOException, MojoExecutionException {
        File inputFile = new File(basedir, relativePath);
        getLog().debug("Processing template: " + inputFile.getAbsolutePath());

        String content = Files.readString(inputFile.toPath(), charset);
        
        Template template;
        try {
            template = engine.parse(content);
        } catch (Exception e) {
            throw new MojoExecutionException("Error parsing template " + relativePath, e);
        }

        TemplateInstance instance = template.instance();

        if (useMavenProperties && project != null) {
            java.util.Properties properties = project.getProperties();
            
            // Flat properties
            for (String key : properties.stringPropertyNames()) {
                instance.data(key, properties.getProperty(key));
            }
            
            // Nested properties for dot notation access (e.g. {maven.compiler.source})
            Map<String, Object> nested = buildNestedProperties(properties);
            for (Map.Entry<String, Object> entry : nested.entrySet()) {
                instance.data(entry.getKey(), entry.getValue());
            }
        }

        if (templateValues != null) {
            for (Map.Entry<String, String> entry : templateValues.entrySet()) {
                instance.data(entry.getKey(), entry.getValue());
            }
        }

        String result = instance.render();

        String outputRelativePath = relativePath;
        if (removeExtension != null && !removeExtension.isEmpty()) {
            if (outputRelativePath.endsWith(removeExtension)) {
                outputRelativePath = outputRelativePath.substring(0, outputRelativePath.length() - removeExtension.length());
            }
        }

        File outputFile = new File(outputDirectory, outputRelativePath);
        
        // Ensure parent directory exists
        File parentDir = outputFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        Files.writeString(outputFile.toPath(), result, charset);
        getLog().info("Generated " + outputFile.getAbsolutePath());
    }

    private Map<String, Object> buildNestedProperties(java.util.Properties properties) {
        Map<String, Object> nested = new java.util.HashMap<>();
        
        // Sort keys to ensure deterministic behavior when keys conflict
        java.util.List<String> keys = new java.util.ArrayList<>(properties.stringPropertyNames());
        keys.sort(java.util.Comparator.comparingInt(String::length));
        
        for (String key : keys) {
            String[] parts = key.split("\\.");
            Map<String, Object> current = nested;
            for (int i = 0; i < parts.length - 1; i++) {
                String part = parts[i];
                Object next = current.get(part);
                if (next == null) {
                    Map<String, Object> newMap = new java.util.HashMap<>();
                    current.put(part, newMap);
                    current = newMap;
                } else if (next instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> existingMap = (Map<String, Object>) next;
                    current = existingMap;
                } else {
                    // Conflict: replace scalar with map
                    Map<String, Object> newMap = new java.util.HashMap<>();
                    current.put(part, newMap);
                    current = newMap;
                }
            }
            
            // If the leaf is already a Map (from a longer key processed previously), 
            // we don't overwrite it to preserve the nested structure.
            // Since we sorted by length, this case won't happen for longer keys overwriting shorter ones,
            // but for equal length it doesn't matter.
            // Actually, shorter keys come first, so a shorter key sets a String.
            // Then a longer key will replace that String with a Map in the loop above.
            Object existingLeaf = current.get(parts[parts.length - 1]);
            if (!(existingLeaf instanceof Map)) {
                current.put(parts[parts.length - 1], properties.getProperty(key));
            }
        }
        return nested;
    }
}
