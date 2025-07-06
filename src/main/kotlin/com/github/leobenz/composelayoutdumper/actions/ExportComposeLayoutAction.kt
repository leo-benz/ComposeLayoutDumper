package com.github.leobenz.composelayoutdumper.actions

import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.LayoutInspectorProjectService
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionInspectorClient
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionPropertiesProvider
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.ViewLayoutInspectorClient
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.util.io.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import org.jetbrains.kotlin.idea.parameterInfo.provideLambdaReturnTypeHints
import java.io.File

class ExportComposeLayoutAction : AnAction() {

    val LOG: Logger = Logger.getInstance(FileChooser::class.java)
    // This method is called to determine if the action should be visible/enabled.
    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project != null) {
            val inspector = LayoutInspectorProjectService.getInstance(project).getLayoutInspector()
            // Only enable the action if there is an active Layout Inspector client connected.
            e.presentation.isEnabledAndVisible = inspector.currentClient?.isConnected == true
        } else {
            e.presentation.isEnabledAndVisible = false
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        LOG.warn("Export compose layout")

        val project = e.project!!
        val inspector = LayoutInspectorProjectService.getInstance(project).getLayoutInspector()
        val client = inspector.currentClient

        if (client !is AppInspectionInspectorClient) {
            LOG.warn("Layout Inspector client is not an AppInspectionInspectorClient")
            return
        }

        // Use IntelliJ's File Chooser to let the user select where to save the file.
        val descriptor = FileSaverDescriptor("Export Compose Layout", "Save layout to JSON file", "json")
        val saveDialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
        val fileWrapper = saveDialog.save("layout.json") ?: return
        val file = fileWrapper.file

        // Run the export logic in a background task with a progress bar.
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
    }

    var propertiesProvider: AppInspectionPropertiesProvider? = null
    private fun exportToJson(inspector: LayoutInspector, client: AppInspectionInspectorClient, file: File, project: Project) {
        propertiesProvider = AppInspectionPropertiesProvider(
            client.viewInspector!!.propertiesCache,
            client.composeInspector!!.parametersCache,
            inspector.inspectorModel,
        )

        propertiesProvider!!.addResultListener { provider, node, table ->
            LOG.warn("Node $node provided")
        }

        runBlocking {
            try {
                // Trigger a refresh to get the latest data
//                client.refresh()

                // Use the inspector model directly - this gives us meaningful, processed data
                val inspectorModel = inspector.inspectorModel

                client.composeInspector!!.getComposeables(inspectorModel.root.drawId, 999, true)
                // Recursively request properties for all nodes in the tree
                requestPropertiesRecursively(inspectorModel.root)

                // Create the JSON structure using the inspector model
                val jsonBuilder = StringBuilder()
                jsonBuilder.appendLine("{")
                
                // Add metadata
                jsonBuilder.appendLine("  \"metadata\": {")
                jsonBuilder.appendLine("    \"timestamp\": ${System.currentTimeMillis()},")
                jsonBuilder.appendLine("    \"format\": \"inspector_model_with_properties\",")
                jsonBuilder.appendLine("    \"processName\": \"${escapeJsonString(getPropertySafely(inspectorModel, "processName")?.toString() ?: "Unknown")}\",")
                jsonBuilder.appendLine("    \"note\": \"Exported from Layout Inspector model with properties and Compose data\"")
                jsonBuilder.appendLine("  },")
                
                // Add windows information
                jsonBuilder.appendLine("  \"windows\": [")
                try {
                    val windows = inspectorModel.windows
                    if (windows != null) {
                        val windowList = windows.entries.toList()
                        windowList.forEachIndexed { index, (_, window) ->
                            jsonBuilder.appendLine("    {")
                            jsonBuilder.appendLine("      \"id\": \"${getPropertySafely(window, "id") ?: "unknown"}\",")
                            jsonBuilder.appendLine("      \"displayName\": \"${escapeJsonString(getPropertySafely(window, "displayName")?.toString() ?: "Unknown")}\",")
                            jsonBuilder.appendLine("      \"isVisible\": ${getPropertySafely(window, "isVisible") ?: false}")
                            jsonBuilder.append("    }")
                            if (index < windowList.size - 1) jsonBuilder.appendLine(",")
                            else jsonBuilder.appendLine()
                        }
                    }
                } catch (e: Exception) {
                    LOG.warn("Failed to access windows", e)
                }
                jsonBuilder.appendLine("  ],")
                
                // Add view hierarchy with properties
                jsonBuilder.appendLine("  \"viewHierarchy\": ")
                val rootView = inspectorModel.root
                if (rootView != null) {
                    // Collect all properties first, then export
                    val allProperties = collectAllProperties(rootView)
                    exportViewNodeWithProperties(rootView, jsonBuilder, 2, allProperties)
                } else {
                    jsonBuilder.append("null")
                }
                jsonBuilder.appendLine(",")

                
                // Add device configuration if available
                jsonBuilder.appendLine("  \"deviceConfiguration\": {")
                jsonBuilder.appendLine("    \"apiLevel\": ${getPropertySafely(inspectorModel, "apiLevel") ?: "unknown"},")
                jsonBuilder.appendLine("    \"resourceLookup\": \"${getPropertySafely(inspectorModel, "resourceLookup") != null}\"")
                jsonBuilder.appendLine("  }")
                
                jsonBuilder.appendLine("}")
                
                // Write to file
                file.writeText(jsonBuilder.toString())
                LOG.info("Successfully exported layout using inspector model with properties: ${file.absolutePath}")
                
            } catch (e: Exception) {
                LOG.error("Failed to export layout from inspector model", e)
                
                // Create minimal JSON structure as fallback
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
        }
    }
    
    private fun requestPropertiesRecursively(viewNode: com.android.tools.idea.layoutinspector.model.ViewNode) {
        try {
            // Request properties for this node
            propertiesProvider!!.requestProperties(viewNode).get()
            LOG.info("Requested properties for node: ${viewNode.drawId}")
            
            // Recursively request properties for all children
            com.android.tools.idea.layoutinspector.model.ViewNode.readAccess {
                viewNode.children.forEach { child ->
                    requestPropertiesRecursively(child)
                }
            }
        } catch (e: Exception) {
            LOG.warn("Failed to request properties for node ${viewNode.drawId}", e)
        }
    }
    
    private suspend fun collectAllProperties(viewNode: com.android.tools.idea.layoutinspector.model.ViewNode): Map<String, Any> {
        val allProperties = mutableMapOf<String, Any>()
        
        // Skip ReusableComposeNodes and Layout nodes
        if (viewNode.qualifiedName == "ReusableComposeNode" || viewNode.qualifiedName == "Layout") {
            // Still collect properties for children, but skip this node itself
            com.android.tools.idea.layoutinspector.model.ViewNode.readAccess {
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
            if (viewNode !is com.android.tools.idea.layoutinspector.model.ComposeViewNode) {
                val cache = (propertiesProvider as AppInspectionPropertiesProvider).let { provider ->
                    val field = provider.javaClass.getDeclaredField("propertiesCache")
                    field.isAccessible = true
                    field.get(provider) as com.android.tools.idea.layoutinspector.pipeline.appinspection.view.ViewPropertiesCache
                }
                val viewData = cache.getDataFor(viewNode)
                if (viewData != null) {
                    allProperties[viewNode.drawId.toString()] = viewData.properties
                }
            } else {
                val cache = (propertiesProvider as AppInspectionPropertiesProvider).let { provider ->
                    val field = provider.javaClass.getDeclaredField("parametersCache")
                    field.isAccessible = true
                    field.get(provider) as? com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.ComposeParametersCache
                }
                val composeData = cache?.getDataFor(viewNode as com.android.tools.idea.layoutinspector.model.ComposeViewNode)
                if (composeData != null) {
                    allProperties[viewNode.drawId.toString()] = composeData.parameters
                }
            }
        } catch (e: Exception) {
            LOG.warn("Failed to collect properties for view ${viewNode.drawId}", e)
        }
        
        // Recursively collect properties for children
        com.android.tools.idea.layoutinspector.model.ViewNode.readAccess {
            viewNode.children.forEach { child ->
                kotlinx.coroutines.runBlocking {
                    allProperties.putAll(collectAllProperties(child))
                }
            }
        }
        
        return allProperties
    }
    
    private fun exportViewNodeWithProperties(viewNode: com.android.tools.idea.layoutinspector.model.ViewNode, jsonBuilder: StringBuilder, indentLevel: Int, allProperties: Map<String, Any>) {
        // Skip ReusableComposeNodes and Layout nodes - they should not appear in output
        // Their children will be flattened into the parent's children array
        if (viewNode.qualifiedName == "ReusableComposeNode" || viewNode.qualifiedName == "Layout") {
            return
        }
        
        val indent = "  ".repeat(indentLevel)
        val indent2 = "  ".repeat(indentLevel + 1)
        
        jsonBuilder.appendLine("{")
        
        // Basic view information
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
        
        // Text value if present
        if (viewNode.textValue.isNotEmpty()) {
            jsonBuilder.appendLine("${indent2}\"textValue\": \"${escapeJsonString(viewNode.textValue)}\",")
        }
        
        // View ID resource reference if available
        viewNode.viewId?.let { viewId ->
            jsonBuilder.appendLine("${indent2}\"viewId\": \"${escapeJsonString(viewId.toString())}\",")
        }
        
        // Layout resource reference if available
        viewNode.layout?.let { layout ->
            jsonBuilder.appendLine("${indent2}\"layout\": \"${escapeJsonString(layout.toString())}\",")
        }
        
        // Get properties from the pre-collected map
        try {
            val propertiesData = allProperties[viewNode.drawId.toString()]
            var hasProperties = false
            
            if (propertiesData != null) {
                if (viewNode !is com.android.tools.idea.layoutinspector.model.ComposeViewNode) {
                    // Regular view properties
                    val propertiesTable = propertiesData as com.android.tools.property.panel.api.PropertiesTable<com.android.tools.idea.layoutinspector.properties.InspectorPropertyItem>
                    if (propertiesTable.values.isNotEmpty()) {
                        hasProperties = true
                        jsonBuilder.appendLine("${indent2}\"properties\": {")
                        
                        val propertyList = propertiesTable.values.toList()
                        propertyList.forEachIndexed { index, property ->
                            exportPropertyWithNesting(
                                property, 
                                jsonBuilder, 
                                "${indent2}  ",
                                index == propertyList.size - 1
                            )
                        }
                        jsonBuilder.appendLine("${indent2}},")
                    }
                } else {
                    // Compose view parameters
                    val parametersTable = propertiesData as com.android.tools.property.panel.api.PropertiesTable<com.android.tools.idea.layoutinspector.properties.InspectorPropertyItem>
                    if (parametersTable.values.isNotEmpty()) {
                        hasProperties = true
                        jsonBuilder.appendLine("${indent2}\"composeParameters\": {")
                        
                        val parameterList = parametersTable.values.toList()
                        parameterList.forEachIndexed { index, parameter ->
                            exportPropertyWithNesting(
                                parameter, 
                                jsonBuilder, 
                                "${indent2}  ",
                                index == parameterList.size - 1
                            )
                        }
                        jsonBuilder.appendLine("${indent2}},")
                    }
                }
            }
            
            if (!hasProperties) {
                jsonBuilder.appendLine("${indent2}\"properties\": {},")
            }
        } catch (e: Exception) {
            LOG.warn("Failed to get properties for view ${viewNode.drawId}", e)
            jsonBuilder.appendLine("${indent2}\"propertiesError\": \"${escapeJsonString(e.message ?: "Unknown error")}\",")
        }
        
        // Children - include all children, but flatten ReusableComposeNodes and Layout nodes
        com.android.tools.idea.layoutinspector.model.ViewNode.readAccess {
            val allChildren = mutableListOf<com.android.tools.idea.layoutinspector.model.ViewNode>()
            
            // Collect all children, flattening ReusableComposeNodes and Layout nodes
            fun collectChildren(node: com.android.tools.idea.layoutinspector.model.ViewNode) {
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
        
        jsonBuilder.append("$indent}")
    }
    
    private fun exportViewNode(viewNode: com.android.tools.idea.layoutinspector.model.ViewNode, jsonBuilder: StringBuilder, indentLevel: Int) {
        val indent = "  ".repeat(indentLevel)
        val indent2 = "  ".repeat(indentLevel + 1)
        
        jsonBuilder.appendLine("{")
        
        // Basic view information
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
        
        // Text value if present
        if (viewNode.textValue.isNotEmpty()) {
            jsonBuilder.appendLine("${indent2}\"textValue\": \"${escapeJsonString(viewNode.textValue)}\",")
        }
        
        // View ID resource reference if available
        viewNode.viewId?.let { viewId ->
            jsonBuilder.appendLine("${indent2}\"viewId\": \"${escapeJsonString(viewId.toString())}\",")
        }
        
        // Layout resource reference if available
        viewNode.layout?.let { layout ->
            jsonBuilder.appendLine("${indent2}\"layout\": \"${escapeJsonString(layout.toString())}\",")
        }
        
        // Children
        com.android.tools.idea.layoutinspector.model.ViewNode.readAccess {
            if (viewNode.children.isNotEmpty()) {
                jsonBuilder.appendLine("${indent2}\"children\": [")
                viewNode.children.forEachIndexed { index, child ->
                    jsonBuilder.append("${indent2}  ")
                    exportViewNode(child, jsonBuilder, indentLevel + 2)
                    if (index < viewNode.children.size - 1) {
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
        
        jsonBuilder.append("$indent}")
    }
    
    private fun exportPropertyWithNesting(
        property: com.android.tools.idea.layoutinspector.properties.InspectorPropertyItem,
        jsonBuilder: StringBuilder,
        indent: String,
        isLast: Boolean = false
    ) {
        val propName = escapeJsonString(property.name)
        val propValue = escapeJsonString(property.value?.toString() ?: "null")
        
        // Check if this is a group property (has nested children)
        if (property is com.android.tools.idea.layoutinspector.properties.InspectorGroupPropertyItem && property.children.isNotEmpty()) {
            // This is a nested/expandable property (View properties)
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
        } else if (property is com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.ParameterGroupItem && property.children.isNotEmpty()) {
            // This is a Compose parameter group (nested parameters like modifier, textStyle, etc.)
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
        } else {
            // Regular property
            jsonBuilder.append("${indent}\"${propName}\": \"$propValue\"")
        }
        
        if (!isLast) {
            jsonBuilder.appendLine(",")
        } else {
            jsonBuilder.appendLine()
        }
    }
    
    private fun escapeJsonString(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
    
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
    
}