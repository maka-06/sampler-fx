package com.example.sampleapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sampleapp.audio.Library
import com.example.sampleapp.audio.LibrarySample

@Composable
fun LibraryScreen(
    library: Library,
    padCount: Int,
    onAddCategory: (String) -> Unit,
    onRenameCategory: (String, String) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onRenameSample: (String, String) -> Unit,
    onDeleteSample: (String) -> Unit,
    onMoveSample: (String, String) -> Unit,
    onPreview: (String) -> Unit,
    onAssignToPad: (Int, String) -> Unit
) {
    var showAddCategory by remember { mutableStateOf(false) }
    // Boîtes de dialogue contextuelles
    var renameCatTarget by remember { mutableStateOf<String?>(null) }
    var renameSampleTarget by remember { mutableStateOf<LibrarySample?>(null) }
    var moveSampleTarget by remember { mutableStateOf<LibrarySample?>(null) }
    var assignTarget by remember { mutableStateOf<LibrarySample?>(null) }
    val expanded = remember { mutableStateOf(library.categories.toSet()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Bibliothèque",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = { showAddCategory = true }) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.size(4.dp))
                    Text("Catégorie")
                }
            }
        }

        library.categories.forEach { category ->
            val samples = library.samples.filter { it.category == category }
            item(key = "cat_$category") {
                CategoryHeader(
                    name = category,
                    count = samples.size,
                    isExpanded = expanded.value.contains(category),
                    onToggle = {
                        expanded.value = if (expanded.value.contains(category)) {
                            expanded.value - category
                        } else {
                            expanded.value + category
                        }
                    },
                    onRename = { renameCatTarget = category },
                    onDelete = { onDeleteCategory(category) }
                )
            }
            if (expanded.value.contains(category)) {
                if (samples.isEmpty()) {
                    item(key = "empty_$category") {
                        Text(
                            "  (vide)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
                        )
                    }
                }
                items(samples.size) { i ->
                    val sample = samples[i]
                    SampleRow(
                        sample = sample,
                        onPreview = { onPreview(sample.id) },
                        onAssign = { assignTarget = sample },
                        onRename = { renameSampleTarget = sample },
                        onMove = { moveSampleTarget = sample },
                        onDelete = { onDeleteSample(sample.id) }
                    )
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showAddCategory) {
        TextInputDialog(
            title = "Nouvelle catégorie",
            label = "Nom",
            initial = "",
            onConfirm = { onAddCategory(it); showAddCategory = false },
            onDismiss = { showAddCategory = false }
        )
    }
    renameCatTarget?.let { cat ->
        TextInputDialog(
            title = "Renommer la catégorie",
            label = "Nom",
            initial = cat,
            onConfirm = { onRenameCategory(cat, it); renameCatTarget = null },
            onDismiss = { renameCatTarget = null }
        )
    }
    renameSampleTarget?.let { s ->
        TextInputDialog(
            title = "Renommer le sample",
            label = "Nom",
            initial = s.name,
            onConfirm = { onRenameSample(s.id, it); renameSampleTarget = null },
            onDismiss = { renameSampleTarget = null }
        )
    }
    moveSampleTarget?.let { s ->
        ChoiceDialog(
            title = "Déplacer vers…",
            options = library.categories,
            onChoose = { onMoveSample(s.id, it); moveSampleTarget = null },
            onDismiss = { moveSampleTarget = null }
        )
    }
    assignTarget?.let { s ->
        ChoiceDialog(
            title = "Assigner au pad",
            options = (0 until padCount).map { "Pad ${it + 1}" },
            onChooseIndex = { onAssignToPad(it, s.id); assignTarget = null },
            onDismiss = { assignTarget = null }
        )
    }
}

@Composable
private fun CategoryHeader(
    name: String,
    count: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null
        )
        Spacer(Modifier.size(8.dp))
        Text(
            "$name ($count)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Options de la catégorie")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Renommer") }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text("Supprimer") }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
private fun SampleRow(
    sample: LibrarySample,
    onPreview: () -> Unit,
    onAssign: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPreview) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Aperçu")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(sample.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${"%.2f".format(sample.durationSec)} s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            TextButton(onClick = onAssign) { Text("→ Pad") }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options du sample")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Renommer") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { menu = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text("Déplacer") },
                        onClick = { menu = false; onMove() }
                    )
                    DropdownMenuItem(
                        text = { Text("Supprimer") },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                        onClick = { menu = false; onDelete() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveToLibraryDialog(
    durationSec: Float,
    categories: List<String>,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedCat by remember { mutableStateOf(categories.firstOrNull() ?: "Défaut") }
    var newCat by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sauver dans la bibliothèque") },
        text = {
            Column {
                Text(
                    "Enregistrement : ${"%.2f".format(durationSec)} s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom du sample") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("Catégorie", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCat == cat && newCat.isBlank(),
                            onClick = { selectedCat = cat; newCat = "" },
                            label = { Text(cat) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newCat,
                    onValueChange = { newCat = it },
                    label = { Text("…ou nouvelle catégorie") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cat = if (newCat.isNotBlank()) newCat.trim() else selectedCat
                onConfirm(name.trim(), cat)
            }) { Text("Sauver") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { if (value.isNotBlank()) onConfirm(value.trim()) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<String>,
    onChoose: ((String) -> Unit)? = null,
    onChooseIndex: ((Int) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(options) { index, option ->
                    Text(
                        option,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onChoose?.invoke(option)
                                onChooseIndex?.invoke(index)
                            }
                            .padding(vertical = 12.dp)
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } }
    )
}
