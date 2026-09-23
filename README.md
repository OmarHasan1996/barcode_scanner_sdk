# ENOC Scanner SDK

A high-performance Android Scanner SDK providing standalone **1D Barcode Scanning** (`CODE_128`, `EAN_13`, `UPC_A`) and **2D Vehicle License Plate Recognition** (UAE/Dubai License Plates) using Google ML Kit Vision OCR.

---

## 🚀 How to Build & Generate the AAR SDK

To generate the release `.aar` library artifact for the Scanner SDK (`:scanner-core`), run the following command in your terminal from the project root:

```bash
./gradlew :scanner-core:assembleRelease
```

### Output Location
Once the build completes successfully, the compiled AAR library is generated at:

```
scanner-core/build/outputs/aar/scanner-core-release.aar
```

To build a debug version of the AAR SDK, run:

```bash
./gradlew :scanner-core:assembleDebug
```

---

## 🛠 Features & Architecture

The SDK is split into two dedicated, single-responsibility analyzers:

1. **`VehiclePlateAnalyzer`** (`VEHICLE_PLATE`):
   - On-device ML Kit OCR vision engine.
   - Central Viewfinder Region-of-Interest (ROI) filtering (ignores surrounding UI menus, tabs, and screen noise).
   - Validates Dubai / UAE plate structures (`D-87550`, `A-80473`, `DXB-5555`).
   - Multi-frame candidate verification for standalone numbers.

2. **`BarcodeAnalyzer`** (`CODE_128`, `EAN_13`, `UPC_A`):
   - Lightweight, industrial multi-pass scanline binarization.
   - Zero ML Kit overhead during barcode scanning.

---

## 📋 Integration Guide

### 1. Add the AAR Dependency
Copy `scanner-core-release.aar` into your application module's `libs/` folder and update your `build.gradle.kts`:

```kotlin
dependencies {
    implementation(files("libs/scanner-core-release.aar"))

    // CameraX Dependencies
    implementation("androidx.camera:camera-core:1.6.1")
    implementation("androidx.camera:camera-camera2:1.6.1")
    implementation("androidx.camera:camera-lifecycle:1.6.1")
    implementation("androidx.camera:camera-view:1.6.1")

    // Google ML Kit Text Recognition (required for Vehicle Plate OCR)
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
```

### 2. Declare Camera Permission
Add the Camera permission to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" android:required="true" />
```

---

## 💻 Code Examples

### Initializing the SDK
```kotlin
val context = applicationContext
val scanner = ScannerSdk.getScanner()

val config = Bundle().apply {
    putBoolean(Scanner.SCANNER_PLAY_BEEP, true)
    putBoolean(Scanner.SCANNER_CONTINUE_SCAN, false)
    putBoolean(Scanner.SCANNER_IS_BACK_CAMERA, true)
    putBoolean(Scanner.SCANNER_IS_LOG_ENABLE, true)
}

scanner.initScanner(config)
```

### Scanning Vehicle License Plates
```kotlin
val scanner = ScannerSdk.getScanner()
scanner.enableAllCodeType(false)
scanner.setCodeTypeOn(Scanner.CodeType.PLATE)
```

### Scanning Barcodes
```kotlin
val scanner = ScannerSdk.getScanner()
scanner.enableAllCodeType(false)
scanner.setCodeTypeOn(Scanner.CodeType.CODE128)
```

### Using `ScannerView` in Compose UI
```kotlin
ScannerView(
    onClose = { /* Handle Close */ },
    onBarcodeDetected = { result ->
        Log.i("APP", "Scanned ${result.format}: ${result.text}")
    }
)
```

---

## 🔍 Logcat & Debugging

All SDK internal logs are tagged with:

```text
ENOC_SCANNER_SDK
```

### Logcat Filter
Filter by tag `ENOC_SCANNER_SDK` to monitor the step-by-step scanning pipeline:
- `[STEP 1 - CAMERA INIT]`: CameraX lifecycle binding.
- `[STEP 2 - FRAME CAPTURED]`: Captured frame dimensions, rotation, and Viewfinder ROI.
- `[STEP 3 - MLKIT DISPATCH]`: ML Kit OCR dispatch.
- `[STEP 4 - OCR OUTPUT]`: Raw OCR text detected.
- `[STEP 5 - OUTSIDE ROI]`: Filtered text outside the camera viewfinder.
- `[STEP 6 - PLATE DETECTED]`: Matched vehicle plate pattern.
- `[STEP 7 - RESULT DISPATCH]`: Result dispatched to host application.

---

## 🧪 Running Unit Tests

Run all unit tests across the SDK library and demo app using:

```bash
./gradlew :scanner-core:testDebugUnitTest :app:testDebugUnitTest
```
