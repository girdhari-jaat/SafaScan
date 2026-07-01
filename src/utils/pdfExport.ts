/**
 * Shares a PDF file using Web Share API if supported (excellent for APKs / mobile browsers),
 * or falls back to our universal saver/sharer.
 */
export async function shareOrDownloadFile(
  blob: Blob,
  fileName: string,
  title?: string,
  forceDownload: boolean = false,
): Promise<void> {
  // Normalize fileName to end with .pdf if not yet present
  let normalizedName = fileName.trim() || "Scanned_Doc";
  if (!normalizedName.toLowerCase().endsWith(".pdf")) {
    normalizedName += ".pdf";
  }

  // FIXED: Added [blob] inside the array bracket
  const file = new File([blob], normalizedName, { type: "application/pdf" });

  // Native Web Share API integration (perfect for Android APK wrapper context)
  if (
    !forceDownload &&
    navigator.share &&
    navigator.canShare &&
    navigator.canShare({ files: [file] })
  ) {
    try {
      await navigator.share({
        files: [file],
        title: title || normalizedName,
        text: "Scanned Document (PDF)",
      });
      return; // Shared natively with success
    } catch (err) {
      if (err instanceof Error && err.name === "AbortError") {
        // User voluntarily dismissed share menu - stop execution, do not download fallback
        return;
      }
      console.warn(
        "Native web share failed, falling back to instant browser downloader:",
        err,
      );
    }
  }

  // Fallback to standard universal downloader/sharer
  await saveOrShareBlob(blob, normalizedName, title, forceDownload);
}
