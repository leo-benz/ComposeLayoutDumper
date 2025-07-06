# Compose Layout Dumper

A comprehensive Android Studio plugin for exporting Jetpack Compose view hierarchies and properties to JSON format. This plugin provides detailed insights into Compose layouts by leveraging Android Studio's Layout Inspector infrastructure to extract and export complete view trees with all properties, including nested/expandable properties like modifiers, text styles, and configurations.

## Features

- **Complete Hierarchy Export**: Exports the entire Compose view tree structure
- **Property Extraction**: Captures all view and Compose properties, including nested properties
- **Layout Inspector Integration**: Leverages Android Studio's existing Layout Inspector for data access
- **JSON Format**: Clean, readable JSON output for analysis and tooling
- **Filtering**: Automatically filters out noise nodes (ReusableComposeNode, Layout) while preserving their children
- **Nested Properties**: Supports expandable properties like modifiers, text styles, and configurations
- **Real-time Export**: Works with live applications connected to the Layout Inspector

## Table of Contents

- [Installation](#installation)
- [Usage](#usage)
- [Architecture](#architecture)
- [JSON Output Format](#json-output-format)
- [Development](#development)
- [Troubleshooting](#troubleshooting)
- [Future Outlook](#future-outlook)
- [Contributing](#contributing)

## Installation

### Prerequisites

- Android Studio (Latest stable version recommended)
- Android SDK with Layout Inspector support
- Android emulator or physical device with debugging enabled

### Building from Source

1. Clone this repository:
   ```bash
   git clone https://github.com/leobenz/ComposeLayoutDumper.git
   cd ComposeLayoutDumper
   ```

2. Build the plugin:
   ```bash
   ./gradlew build
   ```

3. Install the plugin in Android Studio:
   - Go to `File > Settings > Plugins`
   - Click the gear icon and select `Install Plugin from Disk...`
   - Navigate to `build/distributions/` and select the generated `.zip` file
   - Restart Android Studio

## Usage

### Step-by-Step Instructions

1. **Connect to an Emulator/Device**:
   - Launch your Android emulator or connect a physical device
   - Deploy your Compose application to the device

2. **Open Layout Inspector**:
   - In Android Studio, go to `Tools > Layout Inspector`
   - Select your running application from the process list
   - Wait for the Layout Inspector to connect and load the view hierarchy

3. **Export Layout**:
   - Once the Layout Inspector is connected and showing your app's layout
   - Go to `Tools > Export Compose Layout` in the Android Studio menu
   - Choose where to save the JSON file
   - The export will run and save the complete layout hierarchy

### What Gets Exported

The plugin exports:
- Complete view hierarchy structure
- All view properties (bounds, text values, IDs, etc.)
- Compose parameters and their values
- Nested properties (modifiers, text styles, configurations)
- Device configuration information
- Metadata (timestamp, format version, process name)

## Architecture

### High-Level Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                     Android Studio Plugin                      │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐    ┌──────────────────┐    ┌─────────────┐ │
│  │ ExportCompose   │    │   Layout         │    │   JSON      │ │
│  │ LayoutAction    │◄──►│   Inspector      │◄──►│   Export    │ │
│  │                 │    │   Integration    │    │   Engine    │ │
│  └─────────────────┘    └──────────────────┘    └─────────────┘ │
├─────────────────────────────────────────────────────────────────┤
│                    Android Studio APIs                         │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐    ┌──────────────────┐    ┌─────────────┐ │
│  │ LayoutInspector │    │ AppInspection    │    │ ViewNode    │ │
│  │ ProjectService  │◄──►│ PropertiesProvider│◄──►│ Model       │ │
│  └─────────────────┘    └──────────────────┘    └─────────────┘ │
├─────────────────────────────────────────────────────────────────┤
│                       Device Connection                        │
└─────────────────────────────────────────────────────────────────┘
```

### Core Components

#### 1. ExportComposeLayoutAction
**Location**: `src/main/kotlin/com/github/leobenz/composelayoutdumper/actions/ExportComposeLayoutAction.kt`

The main entry point that:
- Implements IntelliJ's `AnAction` interface
- Checks if Layout Inspector is connected before enabling
- Manages the file save dialog
- Orchestrates the export process in a background task

#### 2. Layout Inspector Integration
The plugin deeply integrates with Android Studio's Layout Inspector through:

- **LayoutInspectorProjectService**: Access to the active Layout Inspector instance
- **AppInspectionInspectorClient**: Connection to the device's app inspection framework
- **ViewLayoutInspectorClient**: Access to view-specific inspection data
- **ComposeInspectorClient**: Access to Compose-specific inspection data

#### 3. Properties Provider System
**Key Classes**:
- `AppInspectionPropertiesProvider`: Main interface for property access
- `ViewPropertiesCache`: Caches view properties from the device
- `ComposeParametersCache`: Caches Compose parameters from the device

**Property Collection Flow**:
```
requestProperties(viewNode) → Device API Call → Cache Update → Property Access
```

#### 4. View Node Hierarchy
The plugin works with Android Studio's view model:

- **ViewNode**: Base class representing any view in the hierarchy
- **ComposeViewNode**: Specialized for Compose components
- **InspectorModel**: Root model containing the entire hierarchy

#### 5. Property Types and Nesting
**Property Hierarchy**:
```
InspectorPropertyItem (base)
├── Regular properties (simple key-value)
├── InspectorGroupPropertyItem (expandable view properties)
│   └── children: List<InspectorPropertyItem>
└── ParameterGroupItem (expandable Compose parameters)
    └── children: List<ParameterItem>
```

### Data Flow

1. **Initialization**: Plugin checks for active Layout Inspector connection
2. **Property Request**: Recursively requests properties for all view nodes
3. **Data Collection**: Properties are fetched from device and cached
4. **Hierarchy Traversal**: Plugin walks the view tree structure
5. **Property Extraction**: Accesses cached properties for each node
6. **JSON Generation**: Builds hierarchical JSON with nested properties
7. **File Output**: Saves formatted JSON to user-specified location

### Threading and Concurrency

- **UI Thread**: Action initialization and file dialog
- **Background Thread**: Property collection and JSON generation (using `Task.Backgroundable`)
- **Coroutines**: Async property requests using `runBlocking` and suspend functions
- **Thread Safety**: View node access protected with `ViewNode.readAccess`

## JSON Output Format

### Basic Structure

```json
{
  "metadata": {
    "timestamp": 1701234567890,
    "format": "inspector_model_with_properties",
    "processName": "com.example.app",
    "note": "Exported from Layout Inspector model with properties and Compose data"
  },
  "windows": [...],
  "viewHierarchy": {...},
  "deviceConfiguration": {...}
}
```

### View Node Structure

```json
{
  "id": "12345",
  "qualifiedName": "androidx.compose.material3.Text",
  "layoutBounds": {
    "x": 0,
    "y": 100,
    "width": 200,
    "height": 50
  },
  "renderBounds": {
    "x": 0,
    "y": 100,
    "width": 200,
    "height": 50
  },
  "textValue": "Hello World",
  "properties": {
    "text": "Hello World",
    "color": "Color(0xff000000)",
    "textStyle": {
      "fontSize": "16.sp",
      "fontWeight": "Normal",
      "lineHeight": "20.sp"
    }
  },
  "children": [...]
}
```

### Property Types

- **Simple Properties**: `"propertyName": "value"`
- **Nested Properties**: `"propertyName": { "childProp": "value", ... }`
- **Compose Parameters**: Detailed parameter information for Compose components
- **View Properties**: Traditional Android view attributes

### Filtered Nodes

The following node types are automatically filtered out but their children are preserved:
- `ReusableComposeNode`: Compose recomposition optimization nodes
- `Layout`: Basic layout container nodes

## Development

### Project Structure

```
ComposeLayoutDumper/
├── src/main/kotlin/com/github/leobenz/composelayoutdumper/
│   └── actions/
│       └── ExportComposeLayoutAction.kt
├── src/main/resources/
│   └── META-INF/
│       └── plugin.xml
├── build.gradle.kts
├── gradle.properties
└── README.md
```

### Key Dependencies

- **IntelliJ Platform**: Plugin development framework
- **Android Studio APIs**: Layout Inspector integration
- **Kotlin Coroutines**: Async property collection
- **Protobuf**: Data serialization support

### Building and Testing

```bash
# Build the plugin
./gradlew build

# Run tests
./gradlew test

# Run plugin in development IDE
./gradlew runIde

# Package for distribution
./gradlew buildPlugin
```

### Code Style and Conventions

- **Kotlin**: Primary language
- **Coroutines**: For async operations
- **Reflection**: Limited use for accessing private APIs
- **Error Handling**: Comprehensive try-catch with logging
- **Documentation**: Inline comments for complex logic

## Troubleshooting

### Common Issues

#### Plugin Not Appearing in Tools Menu
- Ensure the plugin is properly installed and Android Studio is restarted
- Check that the Layout Inspector is connected to a running app

#### Export Action Disabled
- Verify that Layout Inspector is actively connected to an app
- Ensure the app has Compose content visible in Layout Inspector

#### Empty or Incomplete Export
- Check that the app is running and responsive
- Verify Layout Inspector can see the view hierarchy
- Look for errors in Android Studio's log (Help > Show Log in Explorer)

#### Property Collection Errors
- Some properties may not be available depending on the Android version
- Check the log for specific property access errors
- Ensure the target app has debugging enabled

### Debug Logging

The plugin logs extensively. To view logs:
1. Go to `Help > Show Log in Explorer`
2. Look for entries containing "Export compose layout" or property collection errors

### Performance Considerations

- Large view hierarchies may take time to export
- Property collection requires device communication
- Memory usage scales with hierarchy size

## Future Outlook

### Standalone Application
**Potential**: Creating a standalone command-line tool for layout export.

**Challenges**:
- Deep integration with Android Studio internals makes extraction difficult
- Layout Inspector APIs are tightly coupled to the IDE
- Device connection and app inspection require Studio's infrastructure

**Possible Approaches**:
1. **ADB Integration**: Direct communication with device inspection services
2. **Studio Headless Mode**: Running Android Studio components without UI
3. **API Extraction**: Reverse-engineering the inspection protocol

### CLI Integration
**Goal**: Enable automated layout analysis in CI/CD pipelines.

**Technical Hurdles**:
- Android Studio's app inspection framework is not designed for headless operation
- Device communication requires Studio's device management layer
- Property collection APIs are private and may change between versions

**Potential Solutions**:
1. **Plugin Extension**: Add command-line interface to existing plugin
2. **Remote API**: Expose layout export through Studio's built-in server
3. **Alternative Data Sources**: Use different approaches like UI Automator dumps

### Architecture Evolution
Future versions might explore:
- **Real-time streaming**: Live layout updates during app interaction
- **Diff analysis**: Comparing layouts between app states
- **Performance metrics**: Including rendering performance data
- **Cross-platform**: Support for other UI frameworks

### Integration Opportunities
- **Design tools**: Integration with Figma, Sketch for design-code comparison
- **Testing frameworks**: Automated UI testing with layout validation
- **Documentation**: Automatic component documentation generation
- **Analytics**: Usage pattern analysis for UI optimization

## Contributing

### Getting Started
1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

### Development Environment
- Android Studio (latest stable)
- JDK 21 or later
- Kotlin plugin
- IntelliJ Platform Plugin SDK

### Guidelines
- Follow existing code style
- Add comprehensive error handling
- Include inline documentation
- Test with various Compose layouts
- Consider performance impact

### Reporting Issues
Please include:
- Android Studio version
- Target app information
- Layout Inspector connection status
- Error logs from Android Studio
- Steps to reproduce

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- Android Studio Layout Inspector team for the foundational APIs
- Jetpack Compose team for the inspection infrastructure
- IntelliJ Platform for the plugin development framework

---

**Note**: This plugin relies on internal Android Studio APIs that may change between versions. While we strive for compatibility, some features may require updates when new Android Studio versions are released.