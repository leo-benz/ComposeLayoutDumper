# Architecture Documentation

## Overview

The Compose Layout Dumper is an Android Studio plugin that leverages the existing Layout Inspector infrastructure to extract and export Jetpack Compose view hierarchies and properties to JSON format. This document provides detailed technical information about the plugin's architecture, implementation decisions, and integration points.

## System Architecture

### Component Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              Android Studio Plugin                             │
│                                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────────┐    │
│  │                         Plugin Layer                                   │    │
│  │                                                                         │    │
│  │  ┌─────────────────┐  ┌────────────────┐  ┌─────────────────────────┐  │    │
│  │  │ ExportCompose   │  │ File Save      │  │ Background Task         │  │    │
│  │  │ LayoutAction    │  │ Dialog         │  │ Management              │  │    │
│  │  │                 │  │                │  │                         │  │    │
│  │  │ - AnAction      │  │ - FileSaver    │  │ - Task.Backgroundable   │  │    │
│  │  │ - Menu Item     │  │ - File         │  │ - Progress Indicator    │  │    │
│  │  │ - Enable Check  │  │   Selection    │  │ - Error Handling        │  │    │
│  │  └─────────────────┘  └────────────────┘  └─────────────────────────┘  │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                        │                                         │
│  ┌─────────────────────────────────────────────────────────────────────────┐    │
│  │                    Layout Inspector Integration                         │    │
│  │                                                                         │    │
│  │  ┌─────────────────┐  ┌────────────────┐  ┌─────────────────────────┐  │    │
│  │  │ Inspector       │  │ Property       │  │ View Hierarchy          │  │    │
│  │  │ Service Access  │  │ Collection     │  │ Navigation              │  │    │
│  │  │                 │  │                │  │                         │  │    │
│  │  │ - Project       │  │ - Recursive    │  │ - Tree Traversal        │  │    │
│  │  │   Service       │  │   Requests     │  │ - Node Filtering        │  │    │
│  │  │ - Client        │  │ - Cache        │  │ - Child Flattening      │  │    │
│  │  │   Validation    │  │   Access       │  │                         │  │    │
│  │  └─────────────────┘  └────────────────┘  └─────────────────────────┘  │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                        │                                         │
│  ┌─────────────────────────────────────────────────────────────────────────┐    │
│  │                        JSON Export Engine                              │    │
│  │                                                                         │    │
│  │  ┌─────────────────┐  ┌────────────────┐  ┌─────────────────────────┐  │    │
│  │  │ Hierarchy       │  │ Property       │  │ JSON                    │  │    │
│  │  │ Serialization   │  │ Nesting        │  │ Generation              │  │    │
│  │  │                 │  │                │  │                         │  │    │
│  │  │ - Node Export   │  │ - Group        │  │ - StringBuilder         │  │    │
│  │  │ - Bounds        │  │   Properties   │  │ - Indentation           │  │    │
│  │  │ - Metadata      │  │ - Parameter    │  │ - Escaping              │  │    │
│  │  │                 │  │   Groups       │  │ - Structure             │  │    │
│  │  └─────────────────┘  └────────────────┘  └─────────────────────────┘  │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        │
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           Android Studio APIs                                  │
│                                                                                 │
│  ┌─────────────────┐  ┌────────────────┐  ┌─────────────────┐  ┌────────────┐  │
│  │ Layout          │  │ App            │  │ View Node       │  │ Property   │  │
│  │ Inspector       │  │ Inspection     │  │ Model           │  │ System     │  │
│  │                 │  │                │  │                 │  │            │  │
│  │ - Service       │  │ - Client       │  │ - ViewNode      │  │ - Items    │  │
│  │ - Connection    │  │ - Properties   │  │ - Hierarchy     │  │ - Groups   │  │
│  │ - State         │  │ - Caches       │  │ - Children      │  │ - Nesting  │  │
│  └─────────────────┘  └────────────────┘  └─────────────────┘  └────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        │
┌─────────────────────────────────────────────────────────────────────────────────┐
│                            Device Connection                                   │
│                                                                                 │
│  ┌─────────────────┐  ┌────────────────┐  ┌─────────────────┐  ┌────────────┐  │
│  │ ADB             │  │ App            │  │ Compose         │  │ View       │  │
│  │ Connection      │  │ Process        │  │ Runtime         │  │ System     │  │
│  │                 │  │                │  │                 │  │            │  │
│  │ - Device        │  │ - Target App   │  │ - Components    │  │ - Layout   │  │
│  │ - Protocol      │  │ - Debug Mode   │  │ - Properties    │  │ - Bounds   │  │
│  │ - Transport     │  │ - Inspection   │  │ - Hierarchy     │  │ - State    │  │
│  └─────────────────┘  └────────────────┘  └─────────────────┘  └────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

## Core Implementation Details

### 1. Plugin Entry Point

**File**: `ExportComposeLayoutAction.kt`

```kotlin
class ExportComposeLayoutAction : AnAction() {
    override fun update(e: AnActionEvent) {
        // Enable only when Layout Inspector is connected
        val project = e.project
        val inspector = LayoutInspectorProjectService.getInstance(project).getLayoutInspector()
        e.presentation.isEnabledAndVisible = inspector.currentClient?.isConnected == true
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        // File dialog → Background task → Export process
    }
}
```

**Key Responsibilities**:
- Menu item registration and visibility control
- Layout Inspector connection validation
- File save dialog management
- Background task orchestration
- Error handling and user feedback

### 2. Layout Inspector Integration

#### Service Access Pattern
```kotlin
val inspector = LayoutInspectorProjectService.getInstance(project).getLayoutInspector()
val client = inspector.currentClient as AppInspectionInspectorClient
```

#### Property Provider Setup
```kotlin
val propertiesProvider = AppInspectionPropertiesProvider(
    client.viewInspector!!.propertiesCache,
    client.composeInspector!!.parametersCache,
    inspector.inspectorModel
)
```

#### Async Property Collection
```kotlin
private fun requestPropertiesRecursively(viewNode: ViewNode) {
    propertiesProvider!!.requestProperties(viewNode).get()
    ViewNode.readAccess {
        viewNode.children.forEach { child ->
            requestPropertiesRecursively(child)
        }
    }
}
```

### 3. Property System Architecture

#### Property Hierarchy
```
PropertiesTable<InspectorPropertyItem>
├── Simple Properties
│   └── InspectorPropertyItem
│       ├── name: String
│       ├── value: Any?
│       ├── type: PropertyType
│       └── source: ResourceReference?
│
├── View Group Properties
│   └── InspectorGroupPropertyItem : InspectorPropertyItem
│       ├── children: List<InspectorPropertyItem>
│       ├── classLocation: SourceLocation?
│       └── (inherits base properties)
│
└── Compose Parameter Groups
    └── ParameterGroupItem : ParameterItem
        ├── children: MutableList<ParameterItem>
        ├── reference: ParameterReference?
        └── (inherits base properties)
```

#### Property Access Pattern
```kotlin
suspend fun collectAllProperties(viewNode: ViewNode): Map<String, Any> {
    // View properties
    val cache = getPropertiesCache()
    val viewData = cache.getDataFor(viewNode)
    
    // Compose parameters
    val composeCache = getParametersCache()
    val composeData = composeCache?.getDataFor(viewNode as ComposeViewNode)
}
```

### 4. JSON Export Engine

#### Hierarchical Export Structure
```kotlin
private fun exportViewNodeWithProperties(
    viewNode: ViewNode,
    jsonBuilder: StringBuilder,
    indentLevel: Int,
    allProperties: Map<String, Any>
) {
    // Node metadata
    // Layout bounds
    // Render bounds
    // Properties (with nesting)
    // Children (recursive)
}
```

#### Property Nesting Implementation
```kotlin
private fun exportPropertyWithNesting(
    property: InspectorPropertyItem,
    jsonBuilder: StringBuilder,
    indent: String,
    isLast: Boolean
) {
    when (property) {
        is InspectorGroupPropertyItem -> {
            // Nested view properties
            exportChildren(property.children)
        }
        is ParameterGroupItem -> {
            // Nested Compose parameters
            exportChildren(property.children)
        }
        else -> {
            // Simple property
            exportSimpleProperty(property)
        }
    }
}
```

## Threading and Concurrency Model

### Thread Usage

1. **UI Thread (EDT)**:
   - Action triggering
   - File dialog
   - Progress indicator updates

2. **Background Thread**:
   - Property collection
   - JSON generation
   - File I/O

3. **Coroutine Context**:
   - Async property requests
   - Suspend function calls

### Synchronization Points

```kotlin
// Thread-safe view access
ViewNode.readAccess {
    viewNode.children.forEach { child ->
        // Safe to access view hierarchy
    }
}

// Async property collection
runBlocking {
    val allProperties = collectAllProperties(rootView)
    // Properties collected, safe to export
}
```

## Data Flow Architecture

### 1. Initialization Phase
```
User Action → Menu Click → Action Enabled Check → File Dialog → Background Task
```

### 2. Property Collection Phase
```
Root Node → Recursive Property Requests → Device Communication → Cache Population
```

### 3. Export Phase
```
Hierarchy Traversal → Property Extraction → JSON Generation → File Writing
```

### 4. Error Handling
```
Exception → Logging → User Notification → Graceful Degradation
```

## Integration Points

### Android Studio APIs

**Primary Dependencies**:
- `com.android.tools.idea.layoutinspector.*`
- `com.intellij.openapi.actionSystem.*`
- `com.intellij.openapi.progress.*`

**Key Interfaces**:
- `AnAction`: Menu integration
- `LayoutInspectorProjectService`: Inspector access
- `AppInspectionInspectorClient`: Device communication
- `ViewNode`: Hierarchy model
- `PropertiesProvider`: Property access

### Device Communication

**Protocol Stack**:
```
Plugin → Layout Inspector → App Inspection → ADB → Device → Target App
```

**Data Flow**:
```
Property Request → Transport Layer → App Inspector → Compose/View Runtime → Response
```

## Performance Characteristics

### Time Complexity
- **Property Collection**: O(n) where n = number of nodes
- **JSON Generation**: O(n × p) where p = average properties per node
- **Memory Usage**: O(n × p) for property storage

### Optimization Strategies
1. **Lazy Property Loading**: Properties loaded on-demand
2. **Cache Utilization**: Reuse Layout Inspector's caches
3. **Background Processing**: Non-blocking UI operations
4. **Stream Processing**: StringBuilder for memory efficiency

## Error Handling Strategy

### Error Categories

1. **Connection Errors**:
   - Layout Inspector not connected
   - Device communication failure
   - App inspection unavailable

2. **Property Access Errors**:
   - Private API access failures
   - Cache synchronization issues
   - Type casting problems

3. **Export Errors**:
   - File I/O failures
   - JSON generation errors
   - Memory limitations

### Recovery Mechanisms

```kotlin
try {
    // Primary operation
} catch (e: Exception) {
    LOG.warn("Operation failed", e)
    // Fallback behavior
    // User notification
    // Graceful degradation
}
```

## Security Considerations

### API Access
- Uses reflection for private API access
- Limited to read-only operations
- No modification of runtime state

### Data Handling
- All data remains local to Android Studio
- No network communication outside existing channels
- Exported files contain only layout information

## Future Architecture Considerations

### Extensibility Points

1. **Export Formats**: Plugin architecture for multiple output formats
2. **Filter System**: Configurable node filtering rules
3. **Property Selection**: User-defined property inclusion/exclusion
4. **Real-time Updates**: Live export during app interaction

### Decoupling Opportunities

1. **Core Export Engine**: Separate from Android Studio dependencies
2. **Property Abstraction**: Generic property model
3. **Transport Layer**: Abstract device communication
4. **Format Plugins**: Pluggable export formats

### Scalability Improvements

1. **Streaming Export**: Handle large hierarchies
2. **Incremental Updates**: Delta exports
3. **Compression**: Reduce output file size
4. **Parallel Processing**: Multi-threaded property collection

## Testing Strategy

### Unit Testing
- Property extraction logic
- JSON generation
- Node filtering algorithms

### Integration Testing
- Layout Inspector integration
- File I/O operations
- Error handling paths

### End-to-End Testing
- Full export workflows
- Various Compose layouts
- Performance benchmarks

## Maintenance and Evolution

### Version Compatibility
- Android Studio API changes
- Layout Inspector evolution
- Compose inspection updates

### Monitoring and Diagnostics
- Comprehensive logging
- Performance metrics
- Error reporting

### Documentation Maintenance
- API changes tracking
- Architecture updates
- User guide updates