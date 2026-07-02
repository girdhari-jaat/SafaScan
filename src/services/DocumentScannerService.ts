import { Capacitor } from '@capacitor/core';
import { DocumentScanner } from '@capacitor-mlkit/document-scanner';

export class DocumentScannerService {
  /**
   * Check if the native scanner is supported on the current platform.
   * Only available on native Android.
   */
  public static isSupported(): boolean {
    return Capacitor.getPlatform() === 'android' && Capacitor.isNativePlatform();
  }

  /**
   * Launch the Google ML Kit Document Scanner on Android.
   * Returns an array of Blobs containing the scanned pages.
   */
  public static async scan(pageLimit?: number): Promise<Blob[]> {
    if (!this.isSupported()) {
      throw new Error('Native Document Scanner is only supported on Android Capacitor app.');
    }

    try {
      // Configure ML Kit scanner options
      const options: any = {
        galleryImportAllowed: true,
        resultFormats: 'JPEG' as const,
        scannerMode: 'FULL' as const,
      };

      if (pageLimit !== undefined) {
        options.pageLimit = pageLimit;
      }

      const result = await DocumentScanner.scanDocument(options);

      if (!result || !result.scannedImages || result.scannedImages.length === 0) {
        return [];
      }

      // Process URIs in parallel and fetch them as Blobs
      const blobs: Blob[] = await Promise.all(
        result.scannedImages.map(async (uri) => {
          const webUrl = Capacitor.convertFileSrc(uri);
          const response = await fetch(webUrl);
          if (!response.ok) {
            throw new Error(`Failed to fetch scanned image: ${uri}`);
          }
          return response.blob();
        })
      );

      return blobs;
    } catch (error) {
      console.error('[DocumentScannerService] scan error:', error);
      throw error;
    }
  }
}
