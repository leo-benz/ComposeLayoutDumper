# Implementation Guide

This document provides detailed technical guidance for understanding, extending, and maintaining the Compose Layout Dumper plugin.

## Table of Contents

- [Getting Started](#getting-started)
- [Code Organization](#code-organization)
- [Key Implementation Details](#key-implementation-details)
- [Android Studio API Usage](#android-studio-api-usage)
- [Property System Deep Dive](#property-system-deep-dive)
- [JSON Export Implementation](#json-export-implementation)
- [Error Handling Patterns](#error-handling-patterns)
- [Performance Considerations](#performance-considerations)
- [Extending the Plugin](#extending-the-plugin)
- [Debugging and Troubleshooting](#debugging-and-troubleshooting)

## Getting Started

### Development Environment Setup

1. **Prerequisites**:
   ```bash
   # Required tools
   - Android Studio (latest stable)
   - JDK 21+
   - Git
   
   # Optional but recommended
   - IntelliJ IDEA Ultimate (for plugin development)
   ```

2. **Clone and Build**:
   ```bash
   git clone https://github.com/leobenz/ComposeLayoutDumper.git
   cd ComposeLayoutDumper
   ./gradlew build
   ```

3. **Development IDE Setup**:
   ```bash
   # Run plugin in development environment
   ./gradlew runIde
   
   # This launches a new Android Studio instance with the plugin installed
   ```

### Project Structure Analysis

```
ComposeLayoutDumper/
├── src/main/kotlin/com/github/leobenz/composelayoutdumper/
│   └── actions/
│       └── ExportComposeLayoutAction.kt          # Main plugin implementation
├── src/main/resources/
│   └── META-INF/
│       └── plugin.xml                            # Plugin configuration
├── build.gradle.kts                              # Build configuration
├── gradle.properties                             # Plugin metadata
└── gradle/
    └── libs.versions.toml                        # Dependency versions
```

## Code Organization

### Main Class Structure

```kotlin
class ExportComposeLayoutAction : AnAction() {
    // Entry points
    override fun update(e: AnActionEvent)          // Menu enablement logic
    override fun actionPerformed(e: AnActionEvent) // Export trigger
    
    // Core functionality
    private fun exportToJson(...)                  // Main export orchestration
    private fun requestPropertiesRecursively(...)  // Property collection
    private fun collectAllProperties(...)          // Async property gathering
    private fun exportViewNodeWithProperties(...)  // Node serialization
    private fun exportPropertyWithNesting(...)     // Property nesting
    
    // Utilities
    private fun escapeJsonString(...)              // JSON string escaping
    private fun getPropertySafely(...)             # Reflection-based access
}
```

### Key Design Patterns

#### 1. Template Method Pattern
```kotlin
override fun actionPerformed(e: AnActionEvent) {
    // Template method with steps:
    validatePreconditions()
    showFileDialog()
    executeInBackground {
        collectProperties()
        generateJson()
        writeToFile()
    }
}
```

#### 2. Visitor Pattern (for hierarchy traversal)
```kotlin
private fun exportViewNodeWithProperties(node: ViewNode, ...) {
    // Visit current node
    exportNodeMetadata(node)
    exportNodeProperties(node)
    
    // Visit children
    node.children.forEach { child ->
        exportViewNodeWithProperties(child, ...)
    }
}
```

#### 3. Strategy Pattern (for property handling)
```kotlin
private fun exportPropertyWithNesting(property: InspectorPropertyItem, ...) {
    when (property) {
        is InspectorGroupPropertyItem -> exportGroupProperty(property)
        is ParameterGroupItem -> exportParameterGroup(property)
        else -> exportSimpleProperty(property)
    }
}
```

## Key Implementation Details

### 1. Layout Inspector Integration

#### Service Access
```kotlin
// Get the Layout Inspector service
val inspector = LayoutInspectorProjectService.getInstance(project).getLayoutInspector()

// Validate connection
val client = inspector.currentClient
if (client !is AppInspectionInspectorClient) {
    // Handle error - not the right client type
    return
}
```

#### Client Validation
```kotlin
override fun update(e: AnActionEvent) {
    val project = e.project
    if (project != null) {
        val inspector = LayoutInspectorProjectService.getInstance(project).getLayoutInspector()
        // Only enable if actively connected
        e.presentation.isEnabledAndVisible = inspector.currentClient?.isConnected == true
    } else {
        e.presentation.isEnabledAndVisible = false
    }
}
```

### 2. Property Provider Setup

#### Creating the Provider
```kotlin
// Initialize property provider with required caches
var propertiesProvider: AppInspectionPropertiesProvider? = null

propertiesProvider = AppInspectionPropertiesProvider(
    client.viewInspector!!.propertiesCache,    // View properties
    client.composeInspector!!.parametersCache, // Compose parameters
    inspector.inspectorModel,                   // View hierarchy model
)
```

#### Property Request Listener
```kotlin
// Optional: Add listener for property updates
propertiesProvider!!.addResultListener { provider, node, table ->
    LOG.warn("Node $node provided")
    // Handle property update notifications
}
```

### 3. Recursive Property Collection

#### Implementation Pattern
```kotlin
private fun requestPropertiesRecursively(viewNode: ViewNode) {
    try {
        // Request properties for current node
        propertiesProvider!!.requestProperties(viewNode).get()
        LOG.info("Requested properties for node: ${viewNode.drawId}")
        
        // Recursively request for children (thread-safe)
        ViewNode.readAccess {
            viewNode.children.forEach { child ->
                requestPropertiesRecursively(child)
            }
        }
    } catch (e: Exception) {
        LOG.warn("Failed to request properties for node ${viewNode.drawId}", e)
    }
}
```

#### Async Property Collection
```kotlin
private suspend fun collectAllProperties(viewNode: ViewNode): Map<String, Any> {
    val allProperties = mutableMapOf<String, Any>()
    
    // Skip filtered nodes but collect their children
    if (viewNode.qualifiedName == "ReusableComposeNode" || viewNode.qualifiedName == "Layout") {
        ViewNode.readAccess {
            viewNode.children.forEach { child ->
                kotlinx.coroutines.runBlocking {
                    allProperties.putAll(collectAllProperties(child))
                }
            }
        }
        return allProperties
    }
    
    // Collect properties for this node
    try {
        if (viewNode !is ComposeViewNode) {
            // Regular view properties
            val cache = getViewPropertiesCache()
            val viewData = cache.getDataFor(viewNode)
            if (viewData != null) {
                allProperties[viewNode.drawId.toString()] = viewData.properties
            }
        } else {
            // Compose parameters
            val cache = getComposeParametersCache()
            val composeData = cache?.getDataFor(viewNode)
            if (composeData != null) {
                allProperties[viewNode.drawId.toString()] = composeData.parameters
            }
        }
    } catch (e: Exception) {
        LOG.warn("Failed to collect properties for view ${viewNode.drawId}", e)
    }
    
    // Recursive collection for children
    // ... (see main implementation)
    
    return allProperties
}
```

## Android Studio API Usage

### Critical API Dependencies

#### Layout Inspector APIs
```kotlin
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.LayoutInspectorProjectService
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionInspectorClient
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionPropertiesProvider
```

#### View Model APIs
```kotlin
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.model.InspectorModel
```

#### Property System APIs
```kotlin
import com.android.tools.idea.layoutinspector.properties.InspectorPropertyItem
import com.android.tools.idea.layoutinspector.properties.InspectorGroupPropertyItem
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.ParameterGroupItem
```

### Reflection-Based Access

#### Accessing Private Caches
```kotlin
private fun getViewPropertiesCache(): ViewPropertiesCache {
    val provider = propertiesProvider as AppInspectionPropertiesProvider
    val field = provider.javaClass.getDeclaredField("propertiesCache")
    field.isAccessible = true
    return field.get(provider) as ViewPropertiesCache
}

private fun getComposeParametersCache(): ComposeParametersCache? {
    val provider = propertiesProvider as AppInspectionPropertiesProvider
    val field = provider.javaClass.getDeclaredField("parametersCache")
    field.isAccessible = true
    return field.get(provider) as? ComposeParametersCache
}
```

#### Safe Property Access
```kotlin
private fun getPropertySafely(obj: Any?, propertyName: String): Any? {
    if (obj == null) return null
    return try {
        // Try field access first
        val field = obj.javaClass.getDeclaredField(propertyName)
        field.isAccessible = true
        field.get(obj)
    } catch (e: Exception) {
        try {
            // Try getter method
            val method = obj.javaClass.getMethod("get${propertyName.replaceFirstChar { it.uppercase() }}")
            method.invoke(obj)
        } catch (e2: Exception) {
            try {
                // Try Kotlin property access
                val method = obj.javaClass.getMethod(propertyName)
                method.invoke(obj)
            } catch (e3: Exception) {
                null
            }
        }
    }
}
```

## Property System Deep Dive

### Property Type Hierarchy

```
PropertiesTable<InspectorPropertyItem>
│
├── InspectorPropertyItem (base class)
│   ├── name: String
│   ├── value: Any?
│   ├── type: PropertyType
│   ├── source: ResourceReference?
│   └── namespace: String
│
├── InspectorGroupPropertyItem : InspectorPropertyItem
│   ├── children: List<InspectorPropertyItem>
│   ├── classLocation: SourceLocation?
│   └── (expandable in UI)
│
└── ParameterGroupItem : ParameterItem : InspectorPropertyItem
    ├── children: MutableList<ParameterItem>
    ├── reference: ParameterReference?
    └── (Compose-specific grouping)
```

### Property Access Patterns

#### View Properties
```kotlin
// Access view properties from cache
val viewData = viewPropertiesCache.getDataFor(viewNode)
val propertiesTable = viewData?.properties

// Iterate through properties
propertiesTable?.values?.forEach { property ->
    when (property) {
        is InspectorGroupPropertyItem -> {
            // Handle nested properties
            property.children.forEach { child ->
                // Process child property
            }
        }
        else -> {
            // Handle simple property
            val name = property.name
            val value = property.value?.toString()
        }
    }
}
```

#### Compose Parameters
```kotlin
// Access Compose parameters from cache
val composeData = composeParametersCache?.getDataFor(composeViewNode)
val parametersTable = composeData?.parameters

// Handle parameter groups
parametersTable?.values?.forEach { parameter ->
    if (parameter is ParameterGroupItem && parameter.children.isNotEmpty()) {
        // Nested Compose parameters (e.g., modifier, textStyle)
        parameter.children.forEach { child ->
            // Process nested parameter
        }
    } else {
        // Simple parameter
        val name = parameter.name
        val value = parameter.value?.toString()
    }
}
```

### Property Nesting Implementation

```kotlin
private fun exportPropertyWithNesting(
    property: InspectorPropertyItem,
    jsonBuilder: StringBuilder,
    indent: String,
    isLast: Boolean = false
) {
    val propName = escapeJsonString(property.name)
    val propValue = escapeJsonString(property.value?.toString() ?: "null")
    
    when {
        // View group properties (e.g., layout parameters, style inheritance)
        property is InspectorGroupPropertyItem && property.children.isNotEmpty() -> {
            jsonBuilder.appendLine("${indent}\"${propName}\": {")
            property.children.forEachIndexed { childIndex, child ->
                exportPropertyWithNesting(
                    child,
                    jsonBuilder,
                    "$indent  ",
                    childIndex == property.children.size - 1
                )
            }
            jsonBuilder.append("${indent}}")
        }
        
        // Compose parameter groups (e.g., modifier chains, text styles)
        property is ParameterGroupItem && property.children.isNotEmpty() -> {
            jsonBuilder.appendLine("${indent}\"${propName}\": {")
            property.children.forEachIndexed { childIndex, child ->
                exportPropertyWithNesting(
                    child,
                    jsonBuilder,
                    "$indent  ",
                    childIndex == property.children.size - 1
                )
            }
            jsonBuilder.append("${indent}}")
        }
        
        // Simple properties
        else -> {
            jsonBuilder.append("${indent}\"${propName}\": \"$propValue\"")
        }
    }
    
    // Handle comma separation
    if (!isLast) {
        jsonBuilder.appendLine(",")
    } else {
        jsonBuilder.appendLine()
    }
}
```

## JSON Export Implementation

### Hierarchical Structure Generation

```kotlin
private fun exportViewNodeWithProperties(
    viewNode: ViewNode,
    jsonBuilder: StringBuilder,
    indentLevel: Int,
    allProperties: Map<String, Any>
) {
    // Skip filtered nodes
    if (viewNode.qualifiedName == "ReusableComposeNode" || viewNode.qualifiedName == "Layout") {
        return
    }
    
    val indent = "  ".repeat(indentLevel)
    val indent2 = "  ".repeat(indentLevel + 1)
    
    jsonBuilder.appendLine("{")
    
    // Basic node information
    jsonBuilder.appendLine("${indent2}\"id\": \"${viewNode.drawId}\",")
    jsonBuilder.appendLine("${indent2}\"qualifiedName\": \"${escapeJsonString(viewNode.qualifiedName)}\",")
    
    // Layout bounds
    jsonBuilder.appendLine("${indent2}\"layoutBounds\": {")
    jsonBuilder.appendLine("${indent2}  \"x\": ${viewNode.layoutBounds.x},")
    jsonBuilder.appendLine("${indent2}  \"y\": ${viewNode.layoutBounds.y},")
    jsonBuilder.appendLine("${indent2}  \"width\": ${viewNode.layoutBounds.width},")
    jsonBuilder.appendLine("${indent2}  \"height\": ${viewNode.layoutBounds.height}")
    jsonBuilder.appendLine("${indent2}},")
    
    // Render bounds
    jsonBuilder.appendLine("${indent2}\"renderBounds\": {")
    val renderBounds = viewNode.renderBounds.bounds
    jsonBuilder.appendLine("${indent2}  \"x\": ${renderBounds.x.toInt()},")
    jsonBuilder.appendLine("${indent2}  \"y\": ${renderBounds.y.toInt()},")
    jsonBuilder.appendLine("${indent2}  \"width\": ${renderBounds.width.toInt()},")
    jsonBuilder.appendLine("${indent2}  \"height\": ${renderBounds.height.toInt()}")
    jsonBuilder.appendLine("${indent2}},")
    
    // Optional properties
    if (viewNode.textValue.isNotEmpty()) {
        jsonBuilder.appendLine("${indent2}\"textValue\": \"${escapeJsonString(viewNode.textValue)}\",")
    }
    
    viewNode.viewId?.let { viewId ->
        jsonBuilder.appendLine("${indent2}\"viewId\": \"${escapeJsonString(viewId.toString())}\",")
    }
    
    viewNode.layout?.let { layout ->
        jsonBuilder.appendLine("${indent2}\"layout\": \"${escapeJsonString(layout.toString())}\",")
    }
    
    // Properties from collected data
    exportNodeProperties(viewNode, jsonBuilder, indent2, allProperties)
    
    // Children with filtering
    exportNodeChildren(viewNode, jsonBuilder, indent2, indentLevel, allProperties)
    
    jsonBuilder.append("$indent}")
}
```

### Child Node Filtering

```kotlin
// Children - include all children, but flatten ReusableComposeNodes and Layout nodes
ViewNode.readAccess {
    val allChildren = mutableListOf<ViewNode>()
    
    // Collect all children, flattening filtered nodes
    fun collectChildren(node: ViewNode) {
        node.children.forEach { child ->
            if (child.qualifiedName == "ReusableComposeNode" || child.qualifiedName == "Layout") {
                // Skip this node but include its children
                collectChildren(child)
            } else {
                // Include this node
                allChildren.add(child)
            }
        }
    }
    
    collectChildren(viewNode)
    
    if (allChildren.isNotEmpty()) {
        jsonBuilder.appendLine("${indent2}\"children\": [")
        allChildren.forEachIndexed { index, child ->
            jsonBuilder.append("${indent2}  ")
            exportViewNodeWithProperties(child, jsonBuilder, indentLevel + 2, allProperties)
            if (index < allChildren.size - 1) {
                jsonBuilder.appendLine(",")
            } else {
                jsonBuilder.appendLine()
            }
        }
        jsonBuilder.appendLine("${indent2}]")
    } else {
        jsonBuilder.appendLine("${indent2}\"children\": []")
    }
}
```

### JSON String Escaping

```kotlin
private fun escapeJsonString(text: String): String {
    return text
        .replace("\\", "\\\\")    // Escape backslashes
        .replace("\"", "\\\"")    // Escape quotes
        .replace("\n", "\\n")     // Escape newlines
        .replace("\r", "\\r")     // Escape carriage returns
        .replace("\t", "\\t")     // Escape tabs
}
```

## Error Handling Patterns

### Comprehensive Error Strategy

```kotlin
try {
    // Main operation
    exportToJson(inspector, client, file, project)
    indicator.text = "Export completed successfully!"
} catch (e: Exception) {
    LOG.error("Failed to export layout", e)
    indicator.text = "Export failed: ${e.message}"
    
    // Create fallback JSON
    val fallbackJson = """
    {
      "metadata": {
        "timestamp": ${System.currentTimeMillis()},
        "error": "Failed to export from inspector model: ${e.message}"
      },
      "layout": null
    }
    """.trimIndent()
    
    file.writeText(fallbackJson)
}
```

### Property Access Error Handling

```kotlin
try {
    val propertiesData = allProperties[viewNode.drawId.toString()]
    if (propertiesData != null) {
        // Process properties
    }
    
    if (!hasProperties) {
        jsonBuilder.appendLine("${indent2}\"properties\": {},")
    }
} catch (e: Exception) {
    LOG.warn("Failed to get properties for view ${viewNode.drawId}", e)
    jsonBuilder.appendLine("${indent2}\"propertiesError\": \"${escapeJsonString(e.message ?: "Unknown error")}\",")
}
```

### Graceful Degradation

```kotlin
// Always provide valid JSON structure
val jsonBuilder = StringBuilder()
jsonBuilder.appendLine("{")

// Add metadata even if export fails
jsonBuilder.appendLine("  \"metadata\": {")
jsonBuilder.appendLine("    \"timestamp\": ${System.currentTimeMillis()},")
jsonBuilder.appendLine("    \"format\": \"inspector_model_with_properties\",")

try {
    // Main export logic
    addWindowsInfo(jsonBuilder)
    addViewHierarchy(jsonBuilder)
    addDeviceConfiguration(jsonBuilder)
} catch (e: Exception) {
    // Add error information
    jsonBuilder.appendLine("    \"error\": \"${escapeJsonString(e.message ?: "Unknown error")}\",")
    LOG.error("Export failed", e)
}

jsonBuilder.appendLine("  }")
jsonBuilder.appendLine("}")
```

## Performance Considerations

### Memory Management

```kotlin
// Use StringBuilder for efficient string building
val jsonBuilder = StringBuilder()

// Process large hierarchies in chunks
fun processInChunks(nodes: List<ViewNode>) {
    nodes.chunked(100).forEach { chunk ->
        chunk.forEach { node ->
            exportViewNodeWithProperties(node, jsonBuilder, 0, allProperties)
        }
        // Allow garbage collection between chunks
        System.gc()
    }
}
```

### Async Processing

```kotlin
// Background task for non-blocking export
ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Exporting Compose Layout", false) {
    override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = true
        indicator.text = "Fetching Compose layout tree..."
        
        try {
            exportToJson(inspector, client, file, project)
            indicator.text = "Export completed successfully!"
        } catch (e: Exception) {
            LOG.error("Failed to export layout", e)
            indicator.text = "Export failed: ${e.message}"
        }
    }
})
```

### Property Collection Optimization

```kotlin
// Collect all properties before JSON generation
runBlocking {
    // Single async operation to collect all properties
    val allProperties = collectAllProperties(inspectorModel.root)
    
    // Then generate JSON synchronously with collected data
    exportViewNodeWithProperties(rootView, jsonBuilder, 2, allProperties)
}
```

## Extending the Plugin

### Adding New Export Formats

```kotlin
interface ExportFormat {
    fun export(viewNode: ViewNode, properties: Map<String, Any>): String
}

class JsonExportFormat : ExportFormat {
    override fun export(viewNode: ViewNode, properties: Map<String, Any>): String {
        // Current JSON implementation
    }
}

class XmlExportFormat : ExportFormat {
    override fun export(viewNode: ViewNode, properties: Map<String, Any>): String {
        // XML export implementation
    }
}
```

### Adding Property Filters

```kotlin
interface PropertyFilter {
    fun shouldInclude(property: InspectorPropertyItem): Boolean
}

class NamespaceFilter(private val includedNamespaces: Set<String>) : PropertyFilter {
    override fun shouldInclude(property: InspectorPropertyItem): Boolean {
        return property.namespace in includedNamespaces
    }
}
```

### Adding Custom Node Processors

```kotlin
interface NodeProcessor {
    fun process(viewNode: ViewNode): Map<String, Any>
}

class BoundsProcessor : NodeProcessor {
    override fun process(viewNode: ViewNode): Map<String, Any> {
        return mapOf(
            "layoutBounds" to viewNode.layoutBounds,
            "renderBounds" to viewNode.renderBounds,
            "area" to (viewNode.layoutBounds.width * viewNode.layoutBounds.height)
        )
    }
}
```

## Debugging and Troubleshooting

### Logging Configuration

```kotlin
// Use Android Studio's logging system
private val LOG: Logger = Logger.getInstance(ExportComposeLayoutAction::class.java)

// Log levels
LOG.info("Informational message")
LOG.warn("Warning message", exception)
LOG.error("Error message", exception)
LOG.debug("Debug message") // Only in debug builds
```

### Common Debug Scenarios

#### 1. Layout Inspector Connection Issues
```kotlin
override fun update(e: AnActionEvent) {
    val project = e.project
    LOG.debug("Checking Layout Inspector connection for project: $project")
    
    if (project != null) {
        val inspector = LayoutInspectorProjectService.getInstance(project).getLayoutInspector()
        val client = inspector.currentClient
        
        LOG.debug("Current client: $client, Connected: ${client?.isConnected}")
        
        e.presentation.isEnabledAndVisible = client?.isConnected == true
    } else {
        LOG.warn("No project available")
        e.presentation.isEnabledAndVisible = false
    }
}
```

#### 2. Property Collection Debugging
```kotlin
private suspend fun collectAllProperties(viewNode: ViewNode): Map<String, Any> {
    LOG.debug("Collecting properties for node: ${viewNode.drawId} (${viewNode.qualifiedName})")
    
    val allProperties = mutableMapOf<String, Any>()
    
    try {
        if (viewNode !is ComposeViewNode) {
            val cache = getViewPropertiesCache()
            val viewData = cache.getDataFor(viewNode)
            LOG.debug("View data for ${viewNode.drawId}: $viewData")
            
            if (viewData != null) {
                allProperties[viewNode.drawId.toString()] = viewData.properties
                LOG.debug("Added ${viewData.properties.values.size} properties for view ${viewNode.drawId}")
            }
        } else {
            val cache = getComposeParametersCache()
            val composeData = cache?.getDataFor(viewNode)
            LOG.debug("Compose data for ${viewNode.drawId}: $composeData")
            
            if (composeData != null) {
                allProperties[viewNode.drawId.toString()] = composeData.parameters
                LOG.debug("Added ${composeData.parameters.values.size} parameters for compose ${viewNode.drawId}")
            }
        }
    } catch (e: Exception) {
        LOG.warn("Failed to collect properties for view ${viewNode.drawId}", e)
    }
    
    return allProperties
}
```

#### 3. JSON Export Debugging
```kotlin
private fun exportPropertyWithNesting(
    property: InspectorPropertyItem,
    jsonBuilder: StringBuilder,
    indent: String,
    isLast: Boolean = false
) {
    LOG.debug("Exporting property: ${property.name} (${property.javaClass.simpleName})")
    
    val propName = escapeJsonString(property.name)
    val propValue = escapeJsonString(property.value?.toString() ?: "null")
    
    when {
        property is InspectorGroupPropertyItem && property.children.isNotEmpty() -> {
            LOG.debug("Exporting group property with ${property.children.size} children")
            // ... implementation
        }
        property is ParameterGroupItem && property.children.isNotEmpty() -> {
            LOG.debug("Exporting parameter group with ${property.children.size} children")
            // ... implementation
        }
        else -> {
            LOG.debug("Exporting simple property: $propName = $propValue")
            // ... implementation
        }
    }
}
```

### Testing Strategies

#### Unit Testing Example
```kotlin
class ExportComposeLayoutActionTest {
    
    @Test
    fun `test JSON string escaping`() {
        val action = ExportComposeLayoutAction()
        val input = "Text with \"quotes\" and \n newlines"
        val expected = "Text with \\\"quotes\\\" and \\n newlines"
        
        val result = action.escapeJsonString(input)
        
        assertEquals(expected, result)
    }
    
    @Test
    fun `test property collection with mock data`() {
        // Create mock ViewNode and properties
        val mockViewNode = createMockViewNode()
        val mockProperties = createMockProperties()
        
        // Test property collection
        val result = collectProperties(mockViewNode)
        
        // Verify results
        assertNotNull(result)
        assertTrue(result.isNotEmpty())
    }
}
```

### Performance Profiling

```kotlin
// Add timing information for performance analysis
private fun exportToJson(inspector: LayoutInspector, client: AppInspectionInspectorClient, file: File, project: Project) {
    val startTime = System.currentTimeMillis()
    
    try {
        // Property collection timing
        val propertyStartTime = System.currentTimeMillis()
        requestPropertiesRecursively(inspectorModel.root)
        val allProperties = collectAllProperties(inspectorModel.root)
        val propertyTime = System.currentTimeMillis() - propertyStartTime
        LOG.info("Property collection took: ${propertyTime}ms")
        
        // JSON generation timing
        val jsonStartTime = System.currentTimeMillis()
        val jsonBuilder = StringBuilder()
        // ... JSON generation
        val jsonTime = System.currentTimeMillis() - jsonStartTime
        LOG.info("JSON generation took: ${jsonTime}ms")
        
        // File writing timing
        val fileStartTime = System.currentTimeMillis()
        file.writeText(jsonBuilder.toString())
        val fileTime = System.currentTimeMillis() - fileStartTime
        LOG.info("File writing took: ${fileTime}ms")
        
        val totalTime = System.currentTimeMillis() - startTime
        LOG.info("Total export time: ${totalTime}ms")
        
    } catch (e: Exception) {
        LOG.error("Export failed after ${System.currentTimeMillis() - startTime}ms", e)
        throw e
    }
}
```

This implementation guide provides comprehensive technical details for understanding, extending, and maintaining the Compose Layout Dumper plugin. Use it as a reference for development, debugging, and future enhancements.