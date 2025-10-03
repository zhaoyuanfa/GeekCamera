package com.zyf.camera.ui.compose

import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.Surface
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.zyf.camera.R
import com.zyf.camera.domain.model.CameraMode
import com.zyf.camera.domain.model.CameraState
import com.zyf.camera.utils.Logger
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.delay
import kotlin.math.abs

private val AccentColor = Color(0xFF4DD0E1)
private val PreviewBackground = Color.Black
private val OverlayTint = Color.White.copy(alpha = 0.12f)
private val RecordingGlow = Color(0xFFFF5252)

private const val CAMERA_UI_TAG = "CameraScreenUI"

private enum class CameraSheet {
    Modes, QuickActions, Settings
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun CameraScreen(
    hasPermissions: Boolean,
    cameraMode: CameraMode,
    cameraState: CameraState?,
    recordingTime: Long,
    onRequestPermissions: () -> Unit,
    onCapture: () -> Unit,
    onSwitchCamera: () -> Unit,
    onSelectMode: (CameraMode) -> Unit,
    preview: @Composable (Modifier) -> Unit,
    onOpenGallery: () -> Unit = {},
    onMoreOptions: () -> Unit = {}
) {
    if (!hasPermissions) {
        PermissionRationale(onRequestPermissions = {
            Logger.d(CAMERA_UI_TAG, "Permission rationale clicked")
            onRequestPermissions()
        })
        return
    }

    val sheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        skipHalfExpanded = true
    )
    var sheetContent by rememberSaveable { mutableStateOf<CameraSheet?>(null) }
    val scope = rememberCoroutineScope()

    fun showSheet(content: CameraSheet) {
        Logger.d(CAMERA_UI_TAG, "Opening sheet: $content")
        scope.launch {
            sheetContent = content
            sheetState.show()
        }
    }

    fun hideSheet() {
        Logger.d(CAMERA_UI_TAG, "Hiding sheet (current=${sheetContent ?: "none"})")
        scope.launch {
            sheetState.hide()
        }.invokeOnCompletion {
            sheetContent = null
        }
    }

    val loggableOnCapture: () -> Unit = {
        val stateLabel = cameraState?.let { it::class.simpleName } ?: "null"
        Logger.d(CAMERA_UI_TAG, "Capture tapped (mode=${cameraMode.name}, state=$stateLabel)")
        onCapture()
    }
    val loggableOnSelectMode: (CameraMode) -> Unit = { mode ->
        Logger.d(CAMERA_UI_TAG, "Mode selection requested: ${mode.name} (current=${cameraMode.name})")
        onSelectMode(mode)
    }
    val loggableOnSwitchCamera: () -> Unit = {
        Logger.d(CAMERA_UI_TAG, "Switch camera tapped (currentMode=${cameraMode.name})")
        onSwitchCamera()
    }
    val loggableOnOpenGallery: () -> Unit = {
        Logger.d(CAMERA_UI_TAG, "Gallery button tapped")
        onOpenGallery()
    }
    val loggableOnMoreOptions: () -> Unit = {
        Logger.d(CAMERA_UI_TAG, "More options requested")
        onMoreOptions()
    }
    val loggableOnShowMoreModes: () -> Unit = {
        Logger.d(CAMERA_UI_TAG, "More modes button tapped")
        showSheet(CameraSheet.Modes)
    }


    LaunchedEffect(sheetState.currentValue) {
        if (sheetState.currentValue == ModalBottomSheetValue.Hidden) {
            sheetContent = null
        }
    }

    val density = LocalDensity.current
    val statusPadding = WindowInsets.statusBars.asPaddingValues()
    val navigationPadding = WindowInsets.navigationBars.asPaddingValues()
    val statusTopPaddingDp = (WindowInsets.statusBars.getTop(density) / density.density).dp

    ModalBottomSheetLayout(
        sheetState = sheetState,
        sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        sheetBackgroundColor = Color(0xFF111114).copy(alpha = 0.95f),
        sheetContent = {
            when (sheetContent) {
                CameraSheet.Modes -> ModeSheet(
                    currentMode = cameraMode,
                    onSelectMode = { mode ->
                        loggableOnSelectMode(mode)
                        hideSheet()
                    },
                    onClose = ::hideSheet
                )

                CameraSheet.QuickActions -> QuickActionSheet(onClose = ::hideSheet)

                CameraSheet.Settings -> SettingsSheet(
                    onClose = ::hideSheet,
                    onOpenMore = {
                        hideSheet()
                        loggableOnMoreOptions()
                    }
                )

                null -> Spacer(modifier = Modifier.height(1.dp))
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PreviewBackground)
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val localDensity = LocalDensity.current
                val targetRatio = 3f / 4f
                val availableWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                val availableHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                val currentRatio = availableWidthPx / availableHeightPx

                val (previewWidthDp, previewHeightDp) = if (currentRatio > targetRatio) {
                    val heightDp = with(localDensity) { availableHeightPx.toDp() }
                    val widthDp = heightDp * targetRatio
                    widthDp to heightDp
                } else {
                    val widthDp = with(localDensity) { availableWidthPx.toDp() }
                    val heightDp = widthDp / targetRatio
                    widthDp to heightDp
                }

                Box {
                    preview(
                        Modifier
                            .width(previewWidthDp)
                            .height(previewHeightDp)
                            .clip(RoundedCornerShape(28.dp))
                    )
                    
                    // 显示相机初始化状态
                    if (cameraState is CameraState.Initializing) {
                        Box(
                            modifier = Modifier
                                .width(previewWidthDp)
                                .height(previewHeightDp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(Color.Black.copy(alpha = 0.7f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                androidx.compose.material.CircularProgressIndicator(
                                    color = AccentColor,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "相机初始化中...",
                                    color = Color.White,
                                    style = MaterialTheme.typography.body1
                                )
                            }
                        }
                    }
                }
            }

            OverlayTopBar(
                modifier = Modifier.align(Alignment.TopCenter),
                padding = statusPadding,
                onQuickActions = {
                    Logger.d(CAMERA_UI_TAG, "Quick actions button tapped")
                    showSheet(CameraSheet.QuickActions)
                },
                onShowSettings = {
                    Logger.d(CAMERA_UI_TAG, "Settings button tapped")
                    showSheet(CameraSheet.Settings)
                }
            )

            RecordingIndicatorPill(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = statusTopPaddingDp + 64.dp),
                isRecording = cameraState is CameraState.RecordingStarted,
                recordingTime = recordingTime
            )

            BottomActions(
                modifier = Modifier.align(Alignment.BottomCenter),
                padding = navigationPadding,
                cameraMode = cameraMode,
                isRecording = cameraState is CameraState.RecordingStarted,
                onCapture = loggableOnCapture,
                onSelectMode = loggableOnSelectMode,
                onShowMoreModes = loggableOnShowMoreModes,
                onOpenGallery = loggableOnOpenGallery,
                onSwitchCamera = loggableOnSwitchCamera
            )
        }
    }
}

@Composable
private fun OverlayTopBar(
    modifier: Modifier,
    padding: PaddingValues,
    onQuickActions: () -> Unit,
    onShowSettings: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(padding)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = stringResource(id = R.string.camera_overlay_location),
                color = Color.White,
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(id = R.string.camera_overlay_weather),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.caption
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        AspectChip()
        Spacer(modifier = Modifier.width(12.dp))
        SmallRoundButton(
            iconRes = R.drawable.ic_more_options,
            contentDescription = stringResource(id = R.string.camera_open_quick_actions),
            onClick = onQuickActions
        )
        Spacer(modifier = Modifier.width(12.dp))
        SmallRoundButton(
            iconRes = R.drawable.ic_settings_improved,
            contentDescription = stringResource(id = R.string.camera_show_settings),
            onClick = onShowSettings
        )
    }
}

@Composable
private fun AspectChip() {
    Surface(
        color = Color.Black.copy(alpha = 0.45f),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = stringResource(id = R.string.camera_aspect_four_three),
            color = Color.White,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.caption
        )
    }
}

@Composable
private fun RecordingIndicatorPill(
    modifier: Modifier,
    isRecording: Boolean,
    recordingTime: Long
) {
    if (!isRecording && recordingTime <= 0L) return

    Surface(
        modifier = modifier,
        color = RecordingGlow.copy(alpha = 0.22f),
        shape = RoundedCornerShape(50)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(RecordingGlow)
            )
            val minutes = TimeUnit.MILLISECONDS.toMinutes(recordingTime)
            val seconds = TimeUnit.MILLISECONDS.toSeconds(recordingTime) % 60
            Text(
                text = String.format("%02d:%02d", minutes, seconds),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.caption
            )
        }
    }
}

@Composable
private fun BottomActions(
    modifier: Modifier,
    padding: PaddingValues,
    cameraMode: CameraMode,
    isRecording: Boolean,
    onCapture: () -> Unit,
    onSelectMode: (CameraMode) -> Unit,
    onShowMoreModes: () -> Unit,
    onOpenGallery: () -> Unit,
    onSwitchCamera: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(padding)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            ModeSelectorRow(
                selectedMode = cameraMode,
                onModeSelected = onSelectMode,
                modifier = Modifier.align(Alignment.Center)
            )

            SmallRoundButton(
                iconRes = R.drawable.ic_more_options,
                contentDescription = stringResource(id = R.string.camera_open_quick_actions),
                onClick = onShowMoreModes,
                size = 42.dp,
                backgroundColor = Color.Black.copy(alpha = 0.35f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            GalleryPreviewButton(onClick = onOpenGallery)

            Spacer(modifier = Modifier.width(32.dp))

            CaptureButton(
                cameraMode = cameraMode,
                isRecording = isRecording,
                onCapture = onCapture,
                size = 72.dp
            )

            Spacer(modifier = Modifier.width(32.dp))

            SmallRoundButton(
                iconRes = R.drawable.ic_switch_camera_improved,
                contentDescription = stringResource(id = R.string.camera_switch_camera),
                onClick = onSwitchCamera,
                size = 52.dp
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModeSelectorRow(
    selectedMode: CameraMode,
    onModeSelected: (CameraMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val modes = listOf(
        CameraMode.PHOTO,
        CameraMode.VIDEO
    )
    val listState: LazyListState = rememberLazyListState()
    var highlightedMode by remember { mutableStateOf(selectedMode) }

    // 当外部选择变化时，更新高亮并滚动到对应位置
    LaunchedEffect(selectedMode) {
        highlightedMode = selectedMode
        val index = modes.indexOf(selectedMode)
        if (index >= 0) {
            listState.animateScrollToItem(index)
        }
    }

    // 根据滚动状态实时更新高亮项，并在停止时自动发送模式回调
    LaunchedEffect(listState) {
        data class ScrollInfo(val isScrolling: Boolean, val closestIndex: Int)

        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) {
                ScrollInfo(listState.isScrollInProgress, -1)
            } else {
                val viewportStart = layoutInfo.viewportStartOffset
                val viewportEnd = layoutInfo.viewportEndOffset
                val viewportCenter = viewportStart + (viewportEnd - viewportStart) / 2
                val closestIndex = visibleItems.minByOrNull { item ->
                    val itemCenter = item.offset + item.size / 2
                    abs(itemCenter - viewportCenter)
                }?.index ?: -1
                ScrollInfo(listState.isScrollInProgress, closestIndex)
            }
        }
            .filter { it.closestIndex >= 0 }
            .distinctUntilChanged()
            .collectLatest { info ->
                val candidateMode = modes.getOrNull(info.closestIndex) ?: return@collectLatest

                if (candidateMode != highlightedMode) {
                    Logger.d(CAMERA_UI_TAG, "Mode highlight moved to ${candidateMode.name}")
                    highlightedMode = candidateMode
                }

                if (!info.isScrolling && selectedMode != highlightedMode) {
                    onModeSelected(highlightedMode)
                }
            }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth()
    ) {
        val screenWidth = maxWidth
        val halfScreenWidth = screenWidth / 2
        
        LazyRow(
            modifier = Modifier
                .fillMaxWidth(),
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(horizontal = halfScreenWidth),
            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
        ) {
            items(modes, key = { it.name }) { mode ->
                ModeChip(
                    label = when (mode) {
                        CameraMode.PHOTO -> stringResource(id = R.string.camera_mode_photo)
                        CameraMode.VIDEO -> stringResource(id = R.string.camera_mode_video)
                        else -> mode.name
                    },
                    selected = highlightedMode == mode,
                    onClick = { onModeSelected(mode) },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (selected) AccentColor.copy(alpha = 0.18f) else Color.Transparent,
        shape = RoundedCornerShape(16.dp),
        border = if (selected) BorderStroke(1.2.dp, AccentColor.copy(alpha = 0.55f)) else BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        modifier = modifier.clickable {
            Logger.d(CAMERA_UI_TAG, "Mode chip tapped: $label (selected=$selected)")
            onClick()
        }
    ) {
        Text(
            text = label,
            color = if (selected) AccentColor else Color.White,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            style = MaterialTheme.typography.body2,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun SmallRoundButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    backgroundColor: Color = OverlayTint,
    iconTint: Color = Color.White
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(size * 0.42f)
        )
    }
}

@Composable
private fun GalleryPreviewButton(onClick: () -> Unit) {
    Surface(
        color = Color.White.copy(alpha = 0.08f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_gallery_improved),
                contentDescription = stringResource(id = R.string.camera_open_gallery),
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun CaptureButton(
    cameraMode: CameraMode,
    isRecording: Boolean,
    onCapture: () -> Unit,
    size: Dp = 72.dp
) {
    val isVideoMode = cameraMode == CameraMode.VIDEO
    val targetInnerSize = when {
        isVideoMode && isRecording -> size * 0.55f
        isVideoMode -> size * 0.7f
        else -> size * 0.82f
    }
    val targetCorner = when {
        isVideoMode && isRecording -> 10.dp
        isVideoMode -> 16.dp
        else -> 50.dp
    }
    val targetInnerColor = when {
        isVideoMode && isRecording -> RecordingGlow
        isVideoMode -> RecordingGlow.copy(alpha = 0.85f)
        else -> Color.White
    }
    val targetHaloColor = when {
        isVideoMode && isRecording -> RecordingGlow.copy(alpha = 0.3f)
        else -> Color.White.copy(alpha = 0.16f)
    }
    val targetStrokeColor = when {
        isVideoMode && isRecording -> RecordingGlow
        isVideoMode -> RecordingGlow.copy(alpha = 0.65f)
        else -> Color.White.copy(alpha = 0.45f)
    }

    val innerSize by animateDpAsState(targetValue = targetInnerSize, label = "captureInnerSize")
    val corner by animateDpAsState(targetValue = targetCorner, label = "captureCorner")
    val innerColor by animateColorAsState(targetValue = targetInnerColor, label = "captureInnerColor")
    val haloColor by animateColorAsState(targetValue = targetHaloColor, label = "captureHaloColor")
    val strokeColor by animateColorAsState(targetValue = targetStrokeColor, label = "captureStrokeColor")

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(haloColor)
            .border(width = if (isVideoMode) 1.5.dp else 1.dp, color = strokeColor, shape = CircleShape)
            .clickable { onCapture() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size * 0.84f)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (isVideoMode) 0.16f else 0.22f))
        )
        Box(
            modifier = Modifier
                .size(innerSize)
                .clip(RoundedCornerShape(corner))
                .background(innerColor)
        )
    }
}

@Composable
private fun SheetScaffold(
    title: String,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        SheetHandle()
        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.h6,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) {
                Text(
                    text = stringResource(id = R.string.camera_sheet_close),
                    color = AccentColor,
                    style = MaterialTheme.typography.body2
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun SheetHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.2f))
        )
    }
}

@Composable
private fun ModeSheet(
    currentMode: CameraMode,
    onSelectMode: (CameraMode) -> Unit,
    onClose: () -> Unit
) {
    SheetScaffold(title = stringResource(id = R.string.camera_sheet_modes_title), onClose = onClose) {
        ModeSheetItem(
            title = stringResource(id = R.string.camera_mode_photo),
            description = stringResource(id = R.string.camera_mode_description_photo),
            selected = currentMode == CameraMode.PHOTO,
            iconRes = R.drawable.ic_camera,
            onClick = { onSelectMode(CameraMode.PHOTO) }
        )
        Spacer(modifier = Modifier.height(12.dp))
        ModeSheetItem(
            title = stringResource(id = R.string.camera_mode_video),
            description = stringResource(id = R.string.camera_mode_description_video),
            selected = currentMode == CameraMode.VIDEO,
            iconRes = R.drawable.ic_video,
            onClick = { onSelectMode(CameraMode.VIDEO) }
        )
    }
}

@Composable
private fun ModeSheetItem(
    title: String,
    description: String,
    selected: Boolean,
    iconRes: Int,
    onClick: () -> Unit
) {
    val background = if (selected) AccentColor.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f)
    val borderColor = if (selected) AccentColor.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.08f)
    Surface(
        color = background,
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = {
                    Logger.d(CAMERA_UI_TAG, "Mode sheet item tapped: $title (selected=$selected)")
                    onClick()
                })
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(text = title, color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = description,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.body2
                )
            }
            if (selected) {
                Surface(
                    color = Color.White.copy(alpha = 0.9f),
                    shape = CircleShape
                ) {
                    Text(
                        text = "✓",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionSheet(onClose: () -> Unit) {
    SheetScaffold(title = stringResource(id = R.string.camera_sheet_quick_actions_title), onClose = onClose) {
        QuickToggleItem(
            iconRes = R.drawable.ic_flash_auto,
            title = stringResource(id = R.string.camera_toggle_flash),
            subtitle = stringResource(id = R.string.camera_toggle_flash_desc)
        )
        Spacer(modifier = Modifier.height(12.dp))
        QuickToggleItem(
            iconRes = R.drawable.ic_camera,
            title = stringResource(id = R.string.camera_toggle_hdr),
            subtitle = stringResource(id = R.string.camera_toggle_hdr_desc)
        )
        Spacer(modifier = Modifier.height(12.dp))
        QuickToggleItem(
            iconRes = R.drawable.ic_more_options,
            title = stringResource(id = R.string.camera_toggle_timer),
            subtitle = stringResource(id = R.string.camera_toggle_timer_desc)
        )
        Spacer(modifier = Modifier.height(12.dp))
        QuickToggleItem(
            iconRes = R.drawable.ic_settings,
            title = stringResource(id = R.string.camera_toggle_grid),
            subtitle = stringResource(id = R.string.camera_toggle_grid_desc)
        )
    }
}

@Composable
private fun QuickToggleItem(
    iconRes: Int,
    title: String,
    subtitle: String
) {
    var enabled by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = title,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(text = title, color = Color.White, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.caption
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = {
                enabled = it
                Logger.d(CAMERA_UI_TAG, "Quick toggle '$title' changed to $it")
            },
            colors = SwitchDefaults.colors(checkedThumbColor = AccentColor, uncheckedThumbColor = Color.White.copy(alpha = 0.8f))
        )
    }
}

@Composable
private fun SettingsSheet(
    onClose: () -> Unit,
    onOpenMore: () -> Unit
) {
    SheetScaffold(title = stringResource(id = R.string.camera_sheet_settings_title), onClose = onClose) {
        SettingsItem(
            iconRes = R.drawable.ic_camera,
            title = stringResource(id = R.string.camera_setting_resolution),
            subtitle = stringResource(id = R.string.camera_setting_resolution_desc)
        )
        Spacer(modifier = Modifier.height(12.dp))
        SettingsItem(
            iconRes = R.drawable.ic_gallery_improved,
            title = stringResource(id = R.string.camera_setting_storage),
            subtitle = stringResource(id = R.string.camera_setting_storage_desc)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Surface(
            color = AccentColor.copy(alpha = 0.2f),
            shape = RoundedCornerShape(24.dp)
        ) {
            Row(
                modifier = Modifier
                    .clickable(onClick = {
                        Logger.d(CAMERA_UI_TAG, "Settings sheet 'More settings' tapped")
                        onOpenMore()
                    })
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_settings_improved),
                    contentDescription = stringResource(id = R.string.camera_sheet_settings_action_more),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = stringResource(id = R.string.camera_sheet_settings_action_more),
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun SettingsItem(
    iconRes: Int,
    title: String,
    subtitle: String
) {
    Surface(
        color = Color.White.copy(alpha = 0.04f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
            Column {
                Text(text = title, color = Color.White, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.caption
                )
            }
        }
    }
}

@Composable
private fun PermissionRationale(onRequestPermissions: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color.Black, Color(0xFF1A1A1D)))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(id = R.drawable.ic_camera),
                contentDescription = null,
                tint = AccentColor,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(id = R.string.camera_permission_title),
                style = MaterialTheme.typography.h6,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(id = R.string.camera_permission_message),
                style = MaterialTheme.typography.body2,
                color = Color.White.copy(alpha = 0.72f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRequestPermissions) {
                Text(text = stringResource(id = R.string.camera_permission_button))
            }
        }
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onTextureViewReady: (TextureView) -> Unit,
    onSurfaceAvailable: (SurfaceTexture) -> Unit,
    onSurfaceSizeChanged: (TextureView) -> Unit,
    onSurfaceDestroyed: () -> Unit
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextureView(context).apply {
                Logger.d(CAMERA_UI_TAG, "TextureView created")
                onTextureViewReady(this)
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                        Logger.d(CAMERA_UI_TAG, "SurfaceTexture available (size=${'$'}width x ${'$'}height)")
                        onSurfaceAvailable(surface)
                    }

                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                        Logger.d(CAMERA_UI_TAG, "SurfaceTexture size changed to ${'$'}width x ${'$'}height")
                        onSurfaceSizeChanged(this@apply)
                    }

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        Logger.d(CAMERA_UI_TAG, "SurfaceTexture destroyed")
                        onSurfaceDestroyed()
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                        // no-op
                    }
                }
            }
        },
        update = { textureView ->
            if (textureView.surfaceTexture != null) {
                Logger.d(CAMERA_UI_TAG, "TextureView update (size=${textureView.width} x ${textureView.height})")
                onSurfaceSizeChanged(textureView)
            }
        }
    )
}

@Composable
private fun PreviewPlaceholder(modifier: Modifier) {
    Box(
        modifier = modifier
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF0F2027),
                        Color(0xFF203A43),
                        Color(0xFF2C5364)
                    )
                )
            )
    )
}

@Preview(name = "Camera - Photo", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun CameraScreenPhotoPreview() {
    MaterialTheme {
        CameraScreen(
            hasPermissions = true,
            cameraMode = CameraMode.PHOTO,
            cameraState = CameraState.Ready,
            recordingTime = 0L,
            onRequestPermissions = {},
            onCapture = {},
            onSwitchCamera = {},
            onSelectMode = {},
            preview = { modifier -> PreviewPlaceholder(modifier) }
        )
    }
}

@Preview(name = "Camera - Video", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun CameraScreenVideoPreview() {
    MaterialTheme {
        CameraScreen(
            hasPermissions = true,
            cameraMode = CameraMode.VIDEO,
            cameraState = CameraState.RecordingStarted,
            recordingTime = 18_000L,
            onRequestPermissions = {},
            onCapture = {},
            onSwitchCamera = {},
            onSelectMode = {},
            preview = { modifier -> PreviewPlaceholder(modifier) }
        )
    }
}
