import fs from 'fs';
import path from 'path';

const pluginJavaPath = path.resolve(
  process.cwd(),
  'node_modules/@capacitor-community/media/android/src/main/java/com/getcapacitor/community/media/MediaPlugin.java'
);

const pluginTsPath = path.resolve(
  process.cwd(),
  'node_modules/@capacitor-community/media/dist/esm/definitions.d.ts'
);

// 1. Patch the Java class to include scanFile and enhance scanPhoto
if (fs.existsSync(pluginJavaPath)) {
  let content = fs.readFileSync(pluginJavaPath, 'utf8');

  if (!content.includes('public void scanFile(PluginCall call)')) {
    console.log('Patching MediaPlugin.java to implement instant MediaScanner and scanFile method...');

    const target = `    private void scanPhoto(File imageFile) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        Uri contentUri = Uri.fromFile(imageFile);
        mediaScanIntent.setData(contentUri);
        bridge.getActivity().sendBroadcast(mediaScanIntent);
    }`;

    const replacement = `    private void scanPhoto(File imageFile) {
        try {
            android.media.MediaScannerConnection.scanFile(
                getContext(),
                new String[]{imageFile.getAbsolutePath()},
                null,
                new android.media.MediaScannerConnection.OnScanCompletedListener() {
                    @Override
                    public void onScanCompleted(String path, Uri uri) {
                        Log.d("MediaPlugin", "Scanned photo: " + path + " -> uri=" + uri);
                    }
                }
            );
        } catch (Exception e) {
            Log.e("MediaPlugin", "Error scanning photo", e);
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            Uri contentUri = Uri.fromFile(imageFile);
            mediaScanIntent.setData(contentUri);
            bridge.getActivity().sendBroadcast(mediaScanIntent);
        }
    }

    @PluginMethod
    public void scanFile(PluginCall call) {
        String path = call.getString("path");
        if (path == null) {
            call.reject("Path is required");
            return;
        }
        try {
            File file = new File(path);
            if (path.startsWith("file://")) {
                file = new File(Uri.parse(path).getPath());
            } else if (path.startsWith("content://")) {
                file = new File(path);
            }
            final File finalFile = file;
            android.media.MediaScannerConnection.scanFile(
                getContext(),
                new String[]{file.getAbsolutePath()},
                null,
                new android.media.MediaScannerConnection.OnScanCompletedListener() {
                    @Override
                    public void onScanCompleted(String scannedPath, Uri uri) {
                        Log.d("MediaPlugin", "Manual scan completed for " + scannedPath + " -> uri=" + uri);
                        JSObject result = new JSObject();
                        result.put("scanned", true);
                        result.put("uri", uri != null ? uri.toString() : "");
                        call.resolve(result);
                    }
                }
            );
        } catch (Exception e) {
            Log.e("MediaPlugin", "Manual scan failed", e);
            call.reject("Scan failed: " + e.getMessage());
        }
    }`;

    content = content.replace(target, replacement);
    fs.writeFileSync(pluginJavaPath, content, 'utf8');
    console.log('Successfully patched MediaPlugin.java!');
  } else {
    console.log('MediaPlugin.java already patched.');
  }
} else {
  console.error('MediaPlugin.java not found at expected path: ' + pluginJavaPath);
}

// 2. Patch the Typescript definitions to expose scanFile
if (fs.existsSync(pluginTsPath)) {
  let content = fs.readFileSync(pluginTsPath, 'utf8');

  if (!content.includes('scanFile(options: { path: string }): Promise<any>;')) {
    console.log('Patching definitions.d.ts to include scanFile type definitions...');
    
    const target = `    getAlbumsPath(): Promise<AlbumsPathResponse>;`;
    const replacement = `    getAlbumsPath(): Promise<AlbumsPathResponse>;
    /**
     * Instantly triggers the native MediaScanner to scan and refresh a file in the Android media library / gallery.
     */
    scanFile?(options: { path: string }): Promise<{ scanned: boolean; uri: string }>;`;

    content = content.replace(target, replacement);
    fs.writeFileSync(pluginTsPath, content, 'utf8');
    console.log('Successfully patched definitions.d.ts!');
  } else {
    console.log('definitions.d.ts already patched.');
  }
} else {
  console.error('definitions.d.ts not found at expected path: ' + pluginTsPath);
}
