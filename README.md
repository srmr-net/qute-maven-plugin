# Qute Maven Plugin

A Maven plugin for generating files using the [Quarkus Qute](https://quarkus.io/guides/qute) template engine. This plugin allows you to process templates during the build process, injecting Maven project properties and custom values.

## Features

- **Qute Templating**: Leverage the power of Quarkus Qute for flexible and type-safe (where applicable) templating.
- **Maven Property Integration**: Use any Maven project property (including system properties and custom properties defined in your POM) directly in your templates.
- **Nested Property Support**: Access dot-notation properties (e.g., `maven.compiler.source`) using Qute's object-access syntax (e.g., `{maven.compiler.source}`).
- **Configurable Input/Output**: Full control over template locations, file filtering, and output destinations.
- **Extension Removal**: Automatically strip template extensions (like `.qute`) from generated filenames.

## Requirements

- **Java**: 21 or higher (as configured in the plugin's build).
- **Maven**: 3.9.12 or higher.

## Usage

Add the plugin to your `pom.xml`:

```xml
<plugin>
    <groupId>net.srmr</groupId>
    <artifactId>qute-maven-plugin</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <executions>
        <execution>
            <id>generate-files</id>
            <phase>generate-sources</phase>
            <goals>
                <goal>generate</goal>
            </goals>
            <configuration>
                <templateFiles>
                    <directory>src/main/qute</directory>
                    <includes>
                        <include>**/*.qute</include>
                    </includes>
                </templateFiles>
                <outputDirectory>${project.build.directory}/generated-sources/qute</outputDirectory>
                <removeExtension>.qute</removeExtension>
                <templateValues>
                    <appName>${project.name}</appName>
                    <author>John Doe</author>
                </templateValues>
                <useMavenProperties>true</useMavenProperties>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Configuration Parameters

| Parameter | Description | Default Value |
| :--- | :--- | :--- |
| `templateFiles` | **Required.** Defines the directory and patterns for templates. | N/A |
| `outputDirectory` | The directory where generated files are placed. | `${project.build.directory}/generated-sources/qute` |
| `encoding` | File encoding for reading and writing. | `${project.build.sourceEncoding}` |
| `removeExtension` | Optional extension to strip from the output filename. | N/A |
| `templateValues` | A map of custom key-value pairs to pass to the engine. | N/A |
| `useMavenProperties` | Whether to inject Maven project properties as variables. | `true` |

## Maven Property Integration

By default (`useMavenProperties = true`), the plugin makes all Maven project properties available to your templates.

### Flat Access
Properties can be accessed by their full key:
```qute
Project Version: {project.version}
Compiler Source: {maven.compiler.source}
```

### Nested Access
The plugin automatically transforms flat dot-notation keys into nested maps, allowing for cleaner access:
```qute
Source Level: {maven.compiler.source}
```
*Note: In case of conflicts (e.g., both `a.b` and `a.b.c` existing), the longer key structure is preserved.*

## Example

**Template (`src/main/qute/config.yaml.qute`):**
```yaml
app:
  name: "{appName}"
  version: "{project.version}"
  compiler: "{maven.compiler.source}"
generatedBy: "{author}"
```

**Output (`target/generated-sources/qute/config.yaml`):**
```yaml
app:
  name: "My Awesome App"
  version: "1.0-SNAPSHOT"
  compiler: "21"
generatedBy: "John Doe"
```

## License

This project is licensed under the terms found in the [LICENSE](LICENSE) file.
