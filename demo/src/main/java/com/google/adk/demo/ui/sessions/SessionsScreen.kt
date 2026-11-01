package com.google.adk.demo.ui.sessions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    viewModel: SessionsViewModel = viewModel(),
    onSessionSelected: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Sessions") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onSessionSelected("new") }) {
                Icon(Icons.Default.Add, contentDescription = "New Session")
            }
        },
    ) { padding ->
        if (state.sessions.isEmpty() && !state.loading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                Text("No sessions yet. Start a new chat!", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(state.sessions) { session ->
                    ListItem(
                        headlineContent = {
                            Text(session.agentName, maxLines = 1)
                        },
                        supportingContent = {
                            Text(
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                                    .withZone(ZoneId.systemDefault())
                                    .format(session.createdAt),
                            )
                        },
                        modifier = Modifier.clickable { onSessionSelected(session.id) },
                        trailingContent = {
                            IconButton(onClick = { viewModel.deleteSession(session.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        },
                    )
                }
            }
        }
    }
}