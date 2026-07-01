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

// Helper function to insert a snippet into a file if it doesn't already exist
function patchJava() {
  if (!fs.existsSync(pluginJavaPath)) {
    console.error('MediaPlugin.java not found at expected path: ' + pluginJavaPath);
    return;
  }

  let content = fs.readFileSync(pluginJavaPath, 'utf8');
  let changed = false;

  // 1. Add scanFile if not present
  if (!content.includes('public void scanFile(PluginCall call)')) {
    console.log('Adding scanFile method to MediaPlugin.java...');
    
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
    changed = true;
  }

  // 2. Add savePdf if not present
  if (!content.includes('public void savePdf(PluginCall call)')) {
    console.log('Adding savePdf method to MediaPlugin.java...');

    const scanFileMarker = 'public void scanFile(PluginCall call) {';
    if (content.includes(scanFileMarker)) {
      const savePdfSnippet = `    @PluginMethod
    public void savePdf(PluginCall call) {
        String inputPath = call.getString("path");
        String fileName = call.getString("fileName");
        if (inputPath == null) {
            call.reject("Input path (base64 or local path) is required");
            return;
        }

        if (fileName == null) {
            fileName = "SafeScan_" + System.currentTimeMillis();
        }
        
        if (fileName.toLowerCase().endsWith(".pdf")) {
            fileName = fileName.substring(0, fileName.length() - 4);
        }
        final String finalFileName = fileName + ".pdf";

        byte[] pdfBytes = null;
        if (inputPath.startsWith("data:application/pdf;base64,")) {
            try {
                String base64String = inputPath.substring(inputPath.indexOf(",") + 1);
                pdfBytes = Base64.decode(base64String, Base64.DEFAULT);
            } catch (Exception e) {
                call.reject("Failed to decode base64 PDF data", e);
                return;
            }
        } else if (inputPath.startsWith("data:")) {
            try {
                String base64String = inputPath.substring(inputPath.indexOf(",") + 1);
                pdfBytes = Base64.decode(base64String, Base64.DEFAULT);
            } catch (Exception e) {
                call.reject("Failed to decode base64 data", e);
                return;
            }
        } else {
            if (inputPath.length() > 500 || (!inputPath.startsWith("/") && !inputPath.startsWith("file://") && !inputPath.startsWith("content://"))) {
                try {
                    pdfBytes = Base64.decode(inputPath, Base64.DEFAULT);
                } catch (Exception e) {
                    pdfBytes = null;
                }
            }
            
            if (pdfBytes == null) {
                try {
                    File file;
                    if (inputPath.startsWith("file://")) {
                        file = new File(Uri.parse(inputPath).getPath());
                    } else {
                        file = new File(inputPath);
                    }
                    FileInputStream fis = new FileInputStream(file);
                    pdfBytes = new byte[(int) file.length()];
                    int bytesRead = fis.read(pdfBytes);
                    fis.close();
                } catch (Exception e) {
                    call.reject("Failed to read local PDF file: " + e.getMessage());
                    return;
                }
            }
        }

        try {
            Uri relativeUri = null;
            android.content.ContentResolver resolver = getContext().getContentResolver();
            
            if (Build.VERSION.SDK_INT >= 29) {
                android.content.ContentValues values = new android.content.ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, finalFileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SafeScan");
                
                Uri downloadsUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                relativeUri = resolver.insert(downloadsUri, values);
                
                if (relativeUri != null) {
                    try (OutputStream os = resolver.openOutputStream(relativeUri)) {
                        os.write(pdfBytes);
                        os.flush();
                    }
                }
            } else {
                File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File safescanDir = new File(downloadDir, "SafeScan");
                if (!safescanDir.exists()) {
                    safescanDir.mkdirs();
                }
                File pdfFile = new File(safescanDir, finalFileName);
                try (FileOutputStream fos = new FileOutputStream(pdfFile)) {
                    fos.write(pdfBytes);
                    fos.flush();
                }
                relativeUri = Uri.fromFile(pdfFile);
                
                android.media.MediaScannerConnection.scanFile(
                    getContext(),
                    new String[]{pdfFile.getAbsolutePath()},
                    new String[]{"application/pdf"},
                    null
                );
            }

            if (relativeUri != null) {
                JSObject result = new JSObject();
                result.put("filePath", relativeUri.toString());
                call.resolve(result);
            } else {
                call.reject("Failed to save PDF: URI is null");
            }
        } catch (Exception e) {
            Log.e("MediaPlugin", "Error saving PDF via MediaStore", e);
            call.reject("Failed to save PDF: " + e.getMessage());
        }
    }

`;
      content = content.replace('@PluginMethod\n    public void scanFile(PluginCall call) {', savePdfSnippet + '\n    @PluginMethod\n    public void scanFile(PluginCall call) {');
      changed = true;
    }
  }

  // 3. Patch _saveMedia to write directly to public DCIM/<albumName> folder instead of app-private directories
  if (content.includes('private void _saveMedia(PluginCall call) {') && !content.includes('___SAVE MEDIA TO ALBUM (DCIM MODE)')) {
    console.log('Patching _saveMedia to save photos to public DCIM folder...');

    // We will extract everything from "private void _saveMedia(PluginCall call) {" up to "private void _createAlbum(PluginCall call) {"
    const startIndex = content.indexOf('private void _saveMedia(PluginCall call) {');
    const endIndex = content.indexOf('private void _createAlbum(PluginCall call) {');

    if (startIndex !== -1 && endIndex !== -1 && endIndex > startIndex) {
      const originalSaveMedia = content.substring(startIndex, endIndex);
      
      const replacementSaveMedia = `private void _saveMedia(PluginCall call) {
        Log.d("DEBUG LOG", "___SAVE MEDIA TO ALBUM (DCIM MODE)");
        String inputPath = call.getString("path");
        if (inputPath == null) {
            call.reject("Input file path is required", EC_ARG_ERROR);
            return;
        }

        File inputFile;

        if (inputPath.startsWith("data:")) {
            try {
                String base64EncodedString = inputPath.substring(inputPath.indexOf(",") + 1);
                byte[] decodedBytes = Base64.decode(base64EncodedString, Base64.DEFAULT);
                String mime = inputPath.split(";", 2)[0].split(":")[1];
                String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
                if (extension == null || extension.isEmpty()) {
                    call.reject("Cannot identify media type to save image.", EC_ARG_ERROR);
                    return;
                }

                try {
                    inputFile = File.createTempFile("tmp", "." + extension, getContext().getCacheDir());
                    OutputStream os = new FileOutputStream(inputFile);
                    os.write(decodedBytes);
                    os.close();
                } catch (IOException e) {
                    call.reject("Temporary file creation from data URL failed", EC_FS_ERROR);
                    return;
                }
            } catch (Exception e) {
                call.reject("Data URL parsing failed.", EC_ARG_ERROR);
                return;
            }
        } else if (inputPath.startsWith("http://") || inputPath.startsWith("https://")) {
            OkHttpClient client = new OkHttpClient();
            Request okrequest = new Request.Builder().url(inputPath).build();
            try {
                Response response = client.newCall(okrequest).execute();
                if (!response.isSuccessful() || response.body() == null) {
                    throw new IOException();
                }

                String extension = MimeTypeMap.getFileExtensionFromUrl(inputPath);
                if (extension.isEmpty()) {
                    ResponseBody body = response.body();
                    if (body == null) {
                        call.reject("Download failed", EC_DOWNLOAD_ERROR);
                        return;
                    }

                    MediaType mt = body.contentType();
                    if (mt == null) {
                        call.reject("Cannot identify media type to save image.", EC_ARG_ERROR);
                        return;
                    }

                    String mime = mt.type() + "/" + mt.subtype();
                    extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
                }

                if (extension == null || extension.isEmpty()) {
                    call.reject("Cannot identify media type to save image.", EC_ARG_ERROR);
                    return;
                }

                try {
                    inputFile = File.createTempFile("tmp", "." + extension, getContext().getCacheDir());

                    try (InputStream inputStream = response.body().byteStream();
                         OutputStream os = new FileOutputStream(inputFile)) {

                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                    }
                } catch (IOException e) {
                    call.reject("Saving download to device failed.", EC_FS_ERROR);
                    return;
                }
            } catch (IOException e) {
                call.reject("Download failed", EC_DOWNLOAD_ERROR);
                return;
            }
        } else {
            Uri inputUri = Uri.parse(inputPath);
            inputFile = new File(inputUri.getPath());
        }

        String album = call.getString("albumIdentifier");
        String albumName = "SafeScan";
        if (album != null) {
            File tempAlbumFile = new File(album);
            albumName = tempAlbumFile.getName();
            if (albumName.isEmpty() || albumName.equals("/") || album.toLowerCase().equals("safescan")) {
                albumName = "SafeScan";
            }
        }

        try {
            byte[] fileBytes;
            try (InputStream is = new FileInputStream(inputFile)) {
                fileBytes = new byte[(int) inputFile.length()];
                int bytesRead = is.read(fileBytes);
            } catch (IOException e) {
                call.reject("Failed to read input file bytes", e);
                return;
            }

            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmssSSS").format(new Date());
            String fileName = call.getString("fileName", "IMG_" + timeStamp);
            String inputExt = "";
            String absolutePath = inputFile.getAbsolutePath();
            if (absolutePath.contains(".")) {
                inputExt = absolutePath.substring(absolutePath.lastIndexOf("."));
            }
            if (!fileName.toLowerCase().contains(".")) {
                if (!inputExt.isEmpty()) {
                    fileName = fileName + inputExt;
                } else {
                    fileName = fileName + ".png";
                }
            }

            final String finalFileName = fileName;
            String mimeType = "image/png";
            if (finalFileName.toLowerCase().endsWith(".jpg") || finalFileName.toLowerCase().endsWith(".jpeg")) {
                mimeType = "image/jpeg";
            } else if (finalFileName.toLowerCase().endsWith(".gif")) {
                mimeType = "image/gif";
            } else if (finalFileName.toLowerCase().endsWith(".webp")) {
                mimeType = "image/webp";
            }

            Uri targetUri = null;
            android.content.ContentResolver resolver = getContext().getContentResolver();

            if (Build.VERSION.SDK_INT >= 29) {
                android.content.ContentValues values = new android.content.ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, finalFileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/" + albumName);

                Uri imagesUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                targetUri = resolver.insert(imagesUri, values);

                if (targetUri != null) {
                    try (OutputStream os = resolver.openOutputStream(targetUri)) {
                        os.write(fileBytes);
                        os.flush();
                    }
                }
            } else {
                File dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
                File targetSubDir = new File(dcimDir, albumName);
                if (!targetSubDir.exists()) {
                    targetSubDir.mkdirs();
                }
                File imageFile = new File(targetSubDir, finalFileName);
                try (FileOutputStream fos = new FileOutputStream(imageFile)) {
                    fos.write(fileBytes);
                    fos.flush();
                }
                targetUri = Uri.fromFile(imageFile);

                android.media.MediaScannerConnection.scanFile(
                    getContext(),
                    new String[]{imageFile.getAbsolutePath()},
                    new String[]{mimeType},
                    null
                );
            }

            if (targetUri != null) {
                JSObject result = new JSObject();
                result.put("filePath", targetUri.toString());
                call.resolve(result);
            } else {
                call.reject("Failed to save image to public DCIM folder: URI is null");
            }
        } catch (Exception e) {
            Log.e("MediaPlugin", "Error saving photo via MediaStore", e);
            call.reject("Failed to save photo: " + e.getMessage());
        }
    }

    `;

      content = content.substring(0, startIndex) + replacementSaveMedia + content.substring(endIndex);
      changed = true;
    }
  }

  if (changed) {
    fs.writeFileSync(pluginJavaPath, content, 'utf8');
    console.log('Successfully patched MediaPlugin.java with DCIM gallery capabilities!');
  } else {
    console.log('MediaPlugin.java is already up-to-date.');
  }
}

function patchTs() {
  if (!fs.existsSync(pluginTsPath)) {
    console.error('definitions.d.ts not found at expected path: ' + pluginTsPath);
    return;
  }

  let content = fs.readFileSync(pluginTsPath, 'utf8');
  let changed = false;

  // 1. Add scanFile if not present
  if (!content.includes('scanFile(options: { path: string }): Promise<any>;') && !content.includes('scanFile?(')) {
    console.log('Adding scanFile to definitions.d.ts...');
    const target = `    getAlbumsPath(): Promise<AlbumsPathResponse>;`;
    const replacement = `    getAlbumsPath(): Promise<AlbumsPathResponse>;
    /**
     * Instantly triggers the native MediaScanner to scan and refresh a file in the Android media library / gallery.
     */
    scanFile?(options: { path: string }): Promise<{ scanned: boolean; uri: string }>;`;
    content = content.replace(target, replacement);
    changed = true;
  }

  // 2. Add savePdf if not present
  if (!content.includes('savePdf?(') && !content.includes('savePdf(')) {
    console.log('Adding savePdf to definitions.d.ts...');
    const target = `    getAlbumsPath(): Promise<AlbumsPathResponse>;`;
    const replacement = `    getAlbumsPath(): Promise<AlbumsPathResponse>;
    /**
     * Saves a PDF file directly to MediaStore.Downloads (Android 10+) or standard Downloads/SafeScan directory.
     * @param options path (local file uri or base64) and fileName.
     */
    savePdf?(options: { path: string; fileName?: string }): Promise<{ filePath: string }>;`;
    content = content.replace(target, replacement);
    changed = true;
  }

  if (changed) {
    fs.writeFileSync(pluginTsPath, content, 'utf8');
    console.log('Successfully patched definitions.d.ts!');
  } else {
    console.log('definitions.d.ts is already up-to-date.');
  }
}

patchJava();
patchTs();
