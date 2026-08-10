# Grain MVP Android - Installation Guide

## APK Details
- **File**: `app-debug.apk`
- **Size**: 10.91 MB
- **SHA256 Checksum**: `B6659D17E4C49158EF2828DC367F359B36C7EB7A7297DAEA7243FE481751CABF`
- **Signed with**: APK Signature Scheme v2
- **Supports**: Android 8.0+ (API 26+), all architectures (arm64-v8a, armeabi-v7a, x86, x86_64)
- **Backend**: Uses `https://immaturebackend.onrender.com`
- **API Key**: Included in build

---

## Installation Steps (Choose One Method)

### Method 1: Install from Browser (Easiest) ⭐ Recommended
**This method works for remote devices without USB.**

1. **Download via browser**:
   - Send someone the APK file or upload to Google Drive / Dropbox
   - They open the link on their Android phone in Chrome or Firefox
   - Tap the downloaded APK from the download notification

2. **Enable installation**:
   - If prompted: "Allow installation from this source?"
   - Tap "Settings" → "Install"
   - This enables Chrome/your browser to install APKs

3. **Install**:
   - Tap the APK file again
   - Tap "Install"
   - Wait for installation to complete

4. **Done**:
   - Open the app from the app drawer
   - Grant camera permission when prompted
   - Test the "Network Test" button to verify backend connectivity

---

### Method 2: Install from Files App

1. **Transfer the file**:
   - Email, Drive, Dropbox, or USB transfer to phone

2. **Open Files app**:
   - Open the built-in "Files" app
   - Navigate to where the APK was saved (Downloads or folder)

3. **Install**:
   - Long-press the APK file
   - Tap "Open with" → "Package Installer"
   - Tap "Install"

4. **If nothing happens**:
   - Go to Settings → Apps → Special app access → Install unknown apps
   - Enable "Files" (or Files by Google)
   - Retry step 3

---

### Method 3: Install using File Manager (Alternative)

1. **Enable unknown sources first**:
   ```
   Settings > Apps > Special app access > Install unknown apps
   > Your file manager app > Toggle ON
   ```

2. **Open APK**:
   - Navigate to the downloaded APK using your file manager
   - Tap it to open

3. **Install**:
   - The Package Installer will appear
   - Tap "Install"

---

### Method 4: Advanced - USB + ADB (For Developers)

If you have a USB cable and can run commands from a PC:

```powershell
# On Windows (PowerShell)
adb devices
adb uninstall com.grainmvp.android  # Optional: remove old version
adb install -r app-debug.apk
```

**Advantages**:
- Shows exact error messages if installation fails
- Fastest method
- No need to enable unknown app sources

---

## Troubleshooting

### "Installation blocked" or nothing happens

1. **Enable Unknown Sources**:
   - Settings → Apps → Special app access → **Install unknown apps**
   - Toggle ON for the app you're installing from (Chrome, Files, etc.)
   - Retry installation

2. **Uninstall old version**:
   - Settings → Apps → Search "Grain" or "grainmvp"
   - If found, tap → Uninstall
   - Retry installation

3. **Free storage space**:
   - Settings → Storage
   - Ensure at least 100 MB free space
   - Delete some files if needed, then retry

4. **Try different method**:
   - If browser doesn't work, try Files app
   - If Files doesn't work, try download + open from download manager

---

### Verify Installation

After successful installation:

1. **Find the app**:
   - Open app drawer / all apps
   - Search for "Grain" or "Grain MVP"

2. **Test network connectivity**:
   - Open Grain MVP app
   - Look for "Network Test" or similar feature
   - Grant camera permission if prompted
   - Tap to test — should show success (not connection timeout)

3. **Check Logs** (if USB-connected):
   ```powershell
   adb logcat -s RetrofitClient,okhttp.OkHttpClient | Select-String "POST|response|error"
   ```

---

## If Still Having Issues

**Provide this information**:
1. Android version: Settings → About → Android version
2. Device name/model
3. Exact error message (screenshot)
4. Which installation method you tried

**Then try**:
- Connect device to PC via USB
- Run: `adb install -r app-debug.apk`
- Paste the exact output of any error messages

---

## App Features

- **Camera**: Real-time image capture and processing
- **Network Test**: Verify backend connectivity to `https://immaturebackend.onrender.com`
- **Prediction**: Send images for inference
- **Result Replication**: Manage and track inference results

---

## Security Notes

- APK is **debug-signed** (not for production release)
- Backend URL hardcoded at build time
- API key embedded in binary
- For production: use a release keystore and secure the API key


