# Usage Examples and Output Samples

This document provides practical examples of using the Compose Layout Dumper plugin and shows what the exported JSON looks like for various Compose layouts.

## Table of Contents

- [Basic Usage Workflow](#basic-usage-workflow)
- [Sample Compose Layouts](#sample-compose-layouts)
- [JSON Output Examples](#json-output-examples)
- [Advanced Use Cases](#advanced-use-cases)
- [Analysis and Tooling](#analysis-and-tooling)
- [Common Patterns](#common-patterns)
- [Troubleshooting Examples](#troubleshooting-examples)

## Basic Usage Workflow

### Step-by-Step Process

1. **Setup Your Compose App**:
   ```kotlin
   @Composable
   fun MyApp() {
       MaterialTheme {
           Column(
               modifier = Modifier
                   .fillMaxSize()
                   .padding(16.dp)
           ) {
               Text(
                   text = "Hello World",
                   style = MaterialTheme.typography.headlineMedium,
                   color = MaterialTheme.colorScheme.primary
               )
               Spacer(modifier = Modifier.height(16.dp))
               Button(
                   onClick = { /* action */ },
                   modifier = Modifier.fillMaxWidth()
               ) {
                   Text("Click Me")
               }
           }
       }
   }
   ```

2. **Launch and Connect**:
   - Run your app on an emulator or device
   - Open Android Studio's Layout Inspector (`Tools > Layout Inspector`)
   - Select your app process and wait for connection

3. **Export Layout**:
   - Navigate to `Tools > Export Compose Layout`
   - Choose save location (e.g., `layout_export.json`)
   - Wait for export completion

4. **Analyze Results**:
   - Open the generated JSON file
   - Examine the hierarchy and properties

## Sample Compose Layouts

### Example 1: Simple Text Component

**Compose Code**:
```kotlin
@Composable
fun SimpleText() {
    Text(
        text = "Hello World",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = Color.Blue,
        modifier = Modifier.padding(16.dp)
    )
}
```

**Generated JSON**:
```json
{
  "id": "12345",
  "qualifiedName": "androidx.compose.material3.Text",
  "layoutBounds": {
    "x": 16,
    "y": 100,
    "width": 200,
    "height": 50
  },
  "renderBounds": {
    "x": 16,
    "y": 100,
    "width": 200,
    "height": 50
  },
  "textValue": "Hello World",
  "properties": {
    "text": "Hello World",
    "fontSize": "24.sp",
    "fontWeight": "Bold",
    "color": "Color(0xff0000ff)"
  },
  "children": []
}
```

### Example 2: Column with Nested Components

**Compose Code**:
```kotlin
@Composable
fun ColumnLayout() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Title", style = MaterialTheme.typography.headlineMedium)
        Text("Subtitle", style = MaterialTheme.typography.bodyMedium)
        Button(onClick = {}) {
            Text("Action")
        }
    }
}
```

**Generated JSON**:
```json
{
  "id": "67890",
  "qualifiedName": "androidx.compose.foundation.layout.Column",
  "layoutBounds": {
    "x": 16,
    "y": 50,
    "width": 328,
    "height": 150
  },
  "renderBounds": {
    "x": 16,
    "y": 50,
    "width": 328,
    "height": 150
  },
  "properties": {
    "modifier": {
      "fillMaxWidth": "Dimension.Fill",
      "padding": "16.dp"
    },
    "verticalArrangement": "Arrangement.spacedBy(8.dp)"
  },
  "children": [
    {
      "id": "11111",
      "qualifiedName": "androidx.compose.material3.Text",
      "textValue": "Title",
      "properties": {
        "text": "Title",
        "textStyle": {
          "fontSize": "22.sp",
          "fontWeight": "Normal",
          "lineHeight": "28.sp"
        }
      },
      "children": []
    },
    {
      "id": "22222",
      "qualifiedName": "androidx.compose.material3.Text",
      "textValue": "Subtitle",
      "properties": {
        "text": "Subtitle",
        "textStyle": {
          "fontSize": "14.sp",
          "fontWeight": "Normal",
          "lineHeight": "20.sp"
        }
      },
      "children": []
    },
    {
      "id": "33333",
      "qualifiedName": "androidx.compose.material3.Button",
      "properties": {
        "onClick": "Function reference",
        "colors": {
          "containerColor": "Color(0xff6750a4)",
          "contentColor": "Color(0xffffffff)"
        }
      },
      "children": [
        {
          "id": "44444",
          "qualifiedName": "androidx.compose.material3.Text",
          "textValue": "Action",
          "properties": {
            "text": "Action"
          },
          "children": []
        }
      ]
    }
  ]
}
```

### Example 3: Complex Layout with Modifier Chains

**Compose Code**:
```kotlin
@Composable
fun ComplexLayout() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Gray)
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .align(Alignment.Center),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Card Title",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Card content goes here...",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
```

**Generated JSON (excerpt showing modifier chains)**:
```json
{
  "id": "55555",
  "qualifiedName": "androidx.compose.foundation.layout.Box",
  "properties": {
    "modifier": {
      "fillMaxSize": "Dimension.Fill",
      "background": "Color(0xff808080)",
      "padding": "16.dp"
    }
  },
  "children": [
    {
      "id": "66666",
      "qualifiedName": "androidx.compose.material3.Card",
      "properties": {
        "modifier": {
          "fillMaxWidth": "Dimension.Fill",
          "height": "200.dp",
          "align": "Alignment.Center"
        },
        "elevation": {
          "defaultElevation": "8.dp"
        }
      },
      "children": [...]
    }
  ]
}
```

## JSON Output Examples

### Complete File Structure

```json
{
  "metadata": {
    "timestamp": 1701234567890,
    "format": "inspector_model_with_properties",
    "processName": "com.example.myapp",
    "note": "Exported from Layout Inspector model with properties and Compose data"
  },
  "windows": [
    {
      "id": "1",
      "displayName": "com.example.myapp/com.example.myapp.MainActivity",
      "isVisible": true
    }
  ],
  "viewHierarchy": {
    "id": "root",
    "qualifiedName": "DecorView",
    "layoutBounds": { "x": 0, "y": 0, "width": 360, "height": 800 },
    "renderBounds": { "x": 0, "y": 0, "width": 360, "height": 800 },
    "properties": {},
    "children": [
      {
        "id": "content",
        "qualifiedName": "androidx.compose.ui.platform.ComposeView",
        "properties": {
          "compositionContext": "CompositionContext",
          "hasContent": "true"
        },
        "children": [
          {
            "id": "compose_root",
            "qualifiedName": "androidx.compose.material3.MaterialTheme",
            "properties": {
              "colorScheme": {
                "primary": "Color(0xff6750a4)",
                "onPrimary": "Color(0xffffffff)",
                "secondary": "Color(0xff625b71)"
              },
              "typography": {
                "headlineMedium": {
                  "fontSize": "22.sp",
                  "lineHeight": "28.sp",
                  "fontWeight": "Normal"
                }
              }
            },
            "children": [...]
          }
        ]
      }
    ]
  },
  "deviceConfiguration": {
    "apiLevel": 34,
    "resourceLookup": "true"
  }
}
```

### Property Types Examples

#### 1. Simple Properties
```json
{
  "text": "Hello World",
  "fontSize": "16.sp",
  "color": "Color(0xff000000)",
  "visible": "true",
  "enabled": "true"
}
```

#### 2. Nested Modifier Properties
```json
{
  "modifier": {
    "fillMaxWidth": "Dimension.Fill",
    "padding": "16.dp",
    "background": "Color(0xffffffff)",
    "clickable": {
      "enabled": "true",
      "onClick": "Function reference"
    },
    "semantics": {
      "contentDescription": "Button to submit form",
      "role": "Button"
    }
  }
}
```

#### 3. TextStyle Properties
```json
{
  "textStyle": {
    "fontSize": "16.sp",
    "fontWeight": "Bold",
    "fontFamily": "Default",
    "lineHeight": "24.sp",
    "color": "Color(0xff000000)",
    "textAlign": "Start",
    "textDecoration": "None"
  }
}
```

#### 4. Layout Parameters
```json
{
  "layoutParams": {
    "width": "MATCH_PARENT",
    "height": "WRAP_CONTENT",
    "gravity": "CENTER",
    "margins": {
      "left": "16",
      "top": "8",
      "right": "16",
      "bottom": "8"
    }
  }
}
```

## Advanced Use Cases

### Example 1: LazyColumn with Dynamic Content

**Compose Code**:
```kotlin
@Composable
fun ItemList(items: List<String>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        items(items) { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Text(
                    text = item,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
```

**Key Export Features**:
- LazyColumn properties show layout configuration
- Individual lazy items are captured if visible
- Virtualization details in properties

### Example 2: Custom Composable with State

**Compose Code**:
```kotlin
@Composable
fun Counter() {
    var count by remember { mutableStateOf(0) }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(16.dp)
    ) {
        Text(
            text = "Count: $count",
            style = MaterialTheme.typography.headlineMedium
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { count-- }) {
                Text("-")
            }
            Button(onClick = { count++ }) {
                Text("+")
            }
        }
    }
}
```

**Export Insights**:
- State values are captured at export time
- Dynamic text content shows current state
- Button properties include click handlers

### Example 3: Themed Components

**Compose Code**:
```kotlin
@Composable
fun ThemedContent() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF1EB980),
            secondary = Color(0xFF045D56)
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column {
                Text(
                    text = "Themed Text",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
```

**Theme Information in Export**:
```json
{
  "qualifiedName": "androidx.compose.material3.MaterialTheme",
  "properties": {
    "colorScheme": {
      "primary": "Color(0xff1eb980)",
      "secondary": "Color(0xff045d56)",
      "background": "Color(0xff121212)",
      "surface": "Color(0xff121212)"
    }
  }
}
```

## Analysis and Tooling

### Example Analysis Scripts

#### 1. Property Counter (Python)
```python
import json

def count_properties(json_file):
    with open(json_file, 'r') as f:
        data = json.load(f)
    
    def count_node_properties(node):
        count = len(node.get('properties', {}))
        for child in node.get('children', []):
            count += count_node_properties(child)
        return count
    
    total_properties = count_node_properties(data['viewHierarchy'])
    print(f"Total properties: {total_properties}")

count_properties('layout_export.json')
```

#### 2. Component Usage Analysis
```python
def analyze_components(json_file):
    with open(json_file, 'r') as f:
        data = json.load(f)
    
    components = {}
    
    def traverse(node):
        qualified_name = node.get('qualifiedName', 'Unknown')
        components[qualified_name] = components.get(qualified_name, 0) + 1
        
        for child in node.get('children', []):
            traverse(child)
    
    traverse(data['viewHierarchy'])
    
    print("Component usage:")
    for component, count in sorted(components.items()):
        print(f"  {component}: {count}")

analyze_components('layout_export.json')
```

#### 3. Layout Bounds Analysis
```python
def analyze_layout_bounds(json_file):
    with open(json_file, 'r') as f:
        data = json.load(f)
    
    def get_bounds_info(node):
        bounds = node.get('layoutBounds', {})
        return {
            'area': bounds.get('width', 0) * bounds.get('height', 0),
            'position': (bounds.get('x', 0), bounds.get('y', 0)),
            'size': (bounds.get('width', 0), bounds.get('height', 0))
        }
    
    def traverse(node, depth=0):
        info = get_bounds_info(node)
        name = node.get('qualifiedName', 'Unknown')
        print(f"{'  ' * depth}{name}: {info['size']} at {info['position']}")
        
        for child in node.get('children', []):
            traverse(child, depth + 1)
    
    traverse(data['viewHierarchy'])

analyze_layout_bounds('layout_export.json')
```

## Common Patterns

### Pattern 1: Finding Text Components

```bash
# Using jq to find all Text components
jq '.. | objects | select(.qualifiedName? | contains("Text"))' layout_export.json
```

### Pattern 2: Extracting Modifier Information

```bash
# Get all modifier properties
jq '.. | objects | select(has("properties")) | .properties.modifier? // empty' layout_export.json
```

### Pattern 3: Component Hierarchy Visualization

```python
def print_hierarchy(json_file):
    with open(json_file, 'r') as f:
        data = json.load(f)
    
    def print_tree(node, depth=0):
        indent = "  " * depth
        name = node.get('qualifiedName', 'Unknown')
        node_id = node.get('id', 'no-id')
        text_value = node.get('textValue', '')
        
        display_text = f" [{text_value}]" if text_value else ""
        print(f"{indent}{name} ({node_id}){display_text}")
        
        for child in node.get('children', []):
            print_tree(child, depth + 1)
    
    print_tree(data['viewHierarchy'])

print_hierarchy('layout_export.json')
```

## Troubleshooting Examples

### Issue 1: Missing Properties

**Problem**: Some expected properties don't appear in the export.

**Debugging**:
```json
{
  "id": "12345",
  "qualifiedName": "androidx.compose.material3.Text",
  "propertiesError": "Failed to access properties cache",
  "properties": {}
}
```

**Solution**: Check Layout Inspector connection and ensure properties are loaded.

### Issue 2: Incomplete Hierarchy

**Problem**: Some components are missing from the hierarchy.

**Analysis**: Look for filtered nodes or components that weren't captured:
```python
def find_missing_components(expected_components, json_file):
    with open(json_file, 'r') as f:
        data = json.load(f)
    
    found_components = set()
    
    def traverse(node):
        found_components.add(node.get('qualifiedName', 'Unknown'))
        for child in node.get('children', []):
            traverse(child)
    
    traverse(data['viewHierarchy'])
    
    missing = set(expected_components) - found_components
    if missing:
        print(f"Missing components: {missing}")
    else:
        print("All expected components found")
```

### Issue 3: Performance Problems

**Problem**: Export takes too long for large hierarchies.

**Analysis**: Check the export metadata:
```json
{
  "metadata": {
    "timestamp": 1701234567890,
    "exportDurationMs": 5000,
    "nodeCount": 1500,
    "propertyCount": 12000
  }
}
```

**Optimization**: Consider filtering or exporting specific subtrees.

### Issue 4: Property Nesting Issues

**Problem**: Nested properties appear flattened.

**Example of correct nesting**:
```json
{
  "modifier": {
    "padding": "16.dp",
    "background": "Color(0xffffffff)",
    "semantics": {
      "contentDescription": "Submit button",
      "role": "Button"
    }
  }
}
```

**vs. incorrect flattening**:
```json
{
  "modifier.padding": "16.dp",
  "modifier.background": "Color(0xffffffff)",
  "modifier.semantics.contentDescription": "Submit button"
}
```

This usage guide provides practical examples and patterns for effectively using the Compose Layout Dumper plugin and analyzing the exported data.