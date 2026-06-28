package com.safescan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.safescan.data.ScannerMode
import com.safescan.data.Slot
import com.safescan.scanner.ScannerViewModel
import com.safescan.R

@Composable
fun SlotsScreen(
    viewModel: ScannerViewModel,
    onCaptureClick: () -> Unit,
    onClose: () -> Unit,
    onFlashToggle: () -> Unit,
    onGalleryClick: () -> Unit,
    onSlotClick: (String) -> Unit,
    onSlotLongClick: (String) -> Unit
) {
    val currentMode by viewModel.currentMode.collectAsState()
    val slots by viewModel.slots.collectAsState()
    val autoCrop by viewModel.autoCrop.collectAsState()
    val flashOn by viewModel.flashOn.collectAsState()
    val doubleFocus by viewModel.doubleFocusEnabled.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    Box(modifier = Modifier.fillMaxSize().background(Color.Transparent)) {
        // LAYER 1: Viewfinder Overlay Guides based on Selected Mood
        ViewfinderOverlay(mode = currentMode, modifier = Modifier.fillMaxSize())

        // LAYER 2: Control Panel and Overlays
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // A. TOP BAR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Close Button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Close Scanner", tint = Color.White)
                }

                // Center: Flash & Auto Crop Toggles
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.toggleAutoCrop(!autoCrop) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (autoCrop) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (autoCrop) "Auto Crop ON" else "Auto Crop OFF",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }

                    IconButton(
                        onClick = onFlashToggle,
                        modifier = Modifier.background(
                            if (flashOn) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.5f),
                            CircleShape
                        )
                    ) {
                        Text(
                            text = if (flashOn) "⚡" else "💡",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                // Right: Settings Menu Button
                IconButton(
                    onClick = { viewModel.isSettingsOpen.value = true },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                }
            }

            // B. CENTER INSTRUCTIONS OVERLAY
            val guideText = when (currentMode) {
                ScannerMode.CARD -> "Align Card Inside Cutout"
                ScannerMode.DOCUMENT -> "Align Document Inside Frame"
                ScannerMode.GRID -> "Utilize Grid for Centered Alignment"
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = guideText,
                    color = Color.Yellow,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.75f), shape = RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // C. BOTTOM AREA: Floating Carousel & Premium Control Hub
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // I. Horizontal Slots Carousel Card List
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(115.dp)
                        .background(Color.Black.copy(alpha = 0.4f), shape = RoundedCornerShape(12.dp))
                        .padding(8.dp)
                ) {
                    if (slots.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = "No Slots Available", color = Color.Gray)
                        }
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(slots) { slot ->
                                Box(modifier = Modifier.width(85.dp)) {
                                    SlotItem(
                                        slot = slot,
                                        onClick = { onSlotClick(slot.id) },
                                        onLongClick = { onSlotLongClick(slot.id) },
                                        onClear = { viewModel.clearSlot(slot.id) }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // II. Selector Segmented Tab bar for modes ("Paper", "Card", "Grid")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Row(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                            .padding(4.dp)
                    ) {
                        listOf(
                            ScannerMode.DOCUMENT to "Paper",
                            ScannerMode.CARD to "Card",
                            ScannerMode.GRID to "Grid"
                        ).forEach { (mode, label) ->
                            val selected = currentMode == mode
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { viewModel.switchMode(mode) }
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (selected) Color.White else Color.LightGray,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // III. Premium Camera Action Trigger buttons row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Action: Fallback Import Gallery Picker
                    IconButton(
                        onClick = onGalleryClick,
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Text(text = "🖼️", fontSize = 22.sp)
                    }

                    // Center Action: Large Circular Shutter button
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .border(4.dp, Color.White, CircleShape)
                            .background(Color.Transparent, CircleShape)
                            .clickable { onCaptureClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color.White, CircleShape)
                        )
                    }

                    // Right Action: Done Button (Saves and generates PDF)
                    val hasScans = slots.any { it.bitmap != null }
                    val scannedCount = slots.count { it.bitmap != null }
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(if (hasScans) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.5f))
                            .clickable(enabled = hasScans) {
                                viewModel.exportPdf(context) { file ->
                                    if (file != null) {
                                        try {
                                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                file
                                            )
                                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                type = "application/pdf"
                                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.export_share_pdf)))
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(context, "Sharing error", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        android.widget.Toast.makeText(context, "Export Failed", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            if (scannedCount > 0) {
                                Text(text = "($scannedCount)", color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ViewfinderOverlay(mode: ScannerMode, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        val rectWidth: Float
        val rectHeight: Float
        var isBookMode = false

        when (mode) {
            ScannerMode.CARD -> {
                // Card aspect ratio: 3:2 (approx. 1.5)
                rectWidth = width * 0.82f
                rectHeight = rectWidth / 1.5f
            }
            ScannerMode.DOCUMENT -> {
                // Document aspect ratio: A4 (approx. 1.41)
                rectWidth = width * 0.75f
                rectHeight = rectWidth * 1.35f
            }
            ScannerMode.GRID -> {
                rectWidth = 0f
                rectHeight = 0f
            }
        }

        if (rectWidth > 0f && rectHeight > 0f) {
            val left = (width - rectWidth) / 2f
            val top = (height - rectHeight) / 2f

            // 1. Draw outer darkened scrim rectangles
            drawRect(
                color = Color.Black.copy(alpha = 0.55f),
                topLeft = Offset(0f, 0f),
                size = Size(width, top)
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.55f),
                topLeft = Offset(0f, top + rectHeight),
                size = Size(width, height - (top + rectHeight))
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.55f),
                topLeft = Offset(0f, top),
                size = Size(left, rectHeight)
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.55f),
                topLeft = Offset(left + rectWidth, top),
                size = Size(width - (left + rectWidth), rectHeight)
            )

            // 2. Draw high-contrast target outline
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(left, top),
                size = Size(rectWidth, rectHeight),
                cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                style = Stroke(width = 2.5.dp.toPx())
            )

            // 3. Draw book-divider spine if in dual book mode
            if (isBookMode) {
                drawLine(
                    color = Color.Yellow,
                    start = Offset(width / 2f, top),
                    end = Offset(width / 2f, top + rectHeight),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                )
            }
        } else if (mode == ScannerMode.GRID) {
            // Draw standard 3x3 alignment grids
            drawLine(
                color = Color.White.copy(alpha = 0.35f),
                start = Offset(width / 3f, 0f),
                end = Offset(width / 3f, height),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color.White.copy(alpha = 0.35f),
                start = Offset(width * 2f / 3f, 0f),
                end = Offset(width * 2f / 3f, height),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color.White.copy(alpha = 0.35f),
                start = Offset(0f, height / 3f),
                end = Offset(width, height / 3f),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color.White.copy(alpha = 0.35f),
                start = Offset(0f, height * 2f / 3f),
                end = Offset(width, height * 2f / 3f),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SlotItem(slot: Slot, onClick: () -> Unit, onLongClick: () -> Unit, onClear: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.LightGray)
            .combinedClickable(
                onClick = { if (slot.bitmap == null) onClick() },
                onLongClick = { if (slot.bitmap != null) onLongClick() }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (slot.bitmap != null) {
            Image(
                bitmap = slot.bitmap.asImageBitmap(),
                contentDescription = slot.label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            IconButton(
                onClick = onClear,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear slot",
                    tint = Color.White,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                        .padding(4.dp)
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Add, contentDescription = "Add image", tint = Color.DarkGray)
                Spacer(modifier = Modifier.height(2.dp))
                Text(slot.label, color = Color.DarkGray, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
