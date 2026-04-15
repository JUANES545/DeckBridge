package com.example.deckbridge.ui.connect

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.deckbridge.R
import com.example.deckbridge.ui.onboarding.OnboardingSecondaryCta
import com.example.deckbridge.ui.onboarding.OnboardingTheme
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun QrScanScreen(
    viewModel: PcConnectionViewModel,
    onBack: () -> Unit,
    /** @return true if the payload was accepted (screen may pop). */
    onDecoded: (String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val previewViewState = remember { mutableStateOf<PreviewView?>(null) }
    val scanLock = remember { AtomicBoolean(false) }
    val decodeDone = remember { AtomicBoolean(false) }
    val helpVisible by viewModel.helpVisible.collectAsStateWithLifecycle()
    val addAnotherHost = viewModel.isAddAnotherHostContext
    val qrError by viewModel.qrScanMessageRes.collectAsStateWithLifecycle()
    val qrPhase by viewModel.qrPhaseMessageRes.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.qrScanReset.collect {
            decodeDone.set(false)
            scanLock.set(false)
        }
    }

    LaunchedEffect(qrError) {
        if (qrError != null) {
            delay(4_500L)
            viewModel.consumeQrScanMessage()
        }
    }

    DisposableEffect(previewViewState.value, hasPermission, lifecycleOwner) {
        val previewView = previewViewState.value
        if (previewView == null || !hasPermission) {
            return@DisposableEffect onDispose { }
        }
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val listener = Runnable {
            runCatching {
                val provider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    processFrame(
                        imageProxy = imageProxy,
                        scanner = scanner,
                        scanLock = scanLock,
                        decodeDone = decodeDone,
                    ) { raw ->
                        previewView.post {
                            if (!onDecoded(raw)) {
                                decodeDone.set(false)
                            }
                        }
                    }
                }
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            }
        }
        cameraProviderFuture.addListener(listener, mainExecutor)
        onDispose {
            analysisExecutor.shutdownNow()
            scanner.close()
            scanLock.set(false)
            decodeDone.set(false)
            if (cameraProviderFuture.isDone) {
                runCatching { cameraProviderFuture.get().unbindAll() }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OnboardingTheme.background),
    ) {
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.connect_back),
                    tint = OnboardingTheme.textPrimary,
                )
            }
        }
        qrError?.let { errRes ->
            Text(
                text = stringResource(errRes),
                color = Color(0xFFE57373),
                fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                textAlign = TextAlign.Center,
            )
        }
        qrPhase?.let { phaseRes ->
            Text(
                text = stringResource(phaseRes),
                color = OnboardingTheme.accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                textAlign = TextAlign.Center,
            )
        }
        if (!hasPermission) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.connect_qr_permission_denied),
                    color = OnboardingTheme.textSecondary,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val h = maxHeight
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(h),
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            previewViewState.value = this
                        }
                    },
                    update = { },
                )
                QrCornerOverlay(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(h),
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(OnboardingTheme.background)
                .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            InstructionRow(
                icon = { Icon(Icons.Default.Wifi, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                text = stringResource(R.string.connect_qr_step_lan),
            )
            Spacer(Modifier.height(14.dp))
            InstructionRow(
                icon = {
                    Icon(
                        Icons.Outlined.Computer,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                },
                text = stringResource(
                    if (addAnotherHost) {
                        R.string.connect_qr_step_pc_add_host
                    } else {
                        R.string.connect_qr_step_pc
                    },
                ),
            )
            Spacer(Modifier.height(14.dp))
            InstructionRow(
                icon = {
                    Icon(
                        Icons.Default.QrCode2,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                },
                text = stringResource(R.string.connect_qr_step_frame),
            )
            Spacer(Modifier.height(20.dp))
            OnboardingSecondaryCta(
                text = stringResource(R.string.connect_get_help),
                onClick = { viewModel.openHelp() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (helpVisible) {
        ConnectHelpBottomSheet(
            onDismiss = { viewModel.closeHelp() },
            addAnotherHostContext = addAnotherHost,
        )
    }
    }
}

private fun processFrame(
    imageProxy: ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    scanLock: AtomicBoolean,
    decodeDone: AtomicBoolean,
    onRaw: (String) -> Unit,
) {
    if (decodeDone.get()) {
        imageProxy.close()
        return
    }
    if (!scanLock.compareAndSet(false, true)) {
        imageProxy.close()
        return
    }
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        scanLock.set(false)
        imageProxy.close()
        return
    }
    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            val raw = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
            if (raw != null && decodeDone.compareAndSet(false, true)) {
                onRaw(raw)
            } else {
                scanLock.set(false)
            }
        }
        .addOnFailureListener {
            scanLock.set(false)
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}

@Composable
private fun InstructionRow(
    icon: @Composable () -> Unit,
    text: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(OnboardingTheme.accent),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Text(
            text = text,
            color = OnboardingTheme.textPrimary,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QrCornerOverlay(modifier: Modifier = Modifier) {
    val blue = OnboardingTheme.accent
    val stroke = 4.dp
    val len = 36.dp
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(220.dp)
                .padding(stroke),
        ) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .width(len)
                    .height(stroke)
                    .background(blue),
            )
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .width(stroke)
                    .height(len)
                    .background(blue),
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .width(len)
                    .height(stroke)
                    .background(blue),
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .width(stroke)
                    .height(len)
                    .background(blue),
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .width(len)
                    .height(stroke)
                    .background(blue),
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .width(stroke)
                    .height(len)
                    .background(blue),
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .width(len)
                    .height(stroke)
                    .background(blue),
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .width(stroke)
                    .height(len)
                    .background(blue),
            )
        }
    }
}
