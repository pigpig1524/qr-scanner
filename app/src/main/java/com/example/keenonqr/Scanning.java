package com.example.keenonqr;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.core.content.ContextCompat;
import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Scanning extends AppCompatActivity {
    private ProcessCameraProvider cameraProvider;
    private ExecutorService cameraExecutor;
    private PreviewView previewView;
    private BarcodeScanner scanner;
    private boolean isProcessing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanning);
        previewView = findViewById(R.id.previewView);
        cameraExecutor = Executors.newSingleThreadExecutor();
        startCamera();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK).build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        scanner = BarcodeScanning.getClient();

        imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
            if (isProcessing) {
                imageProxy.close();
                return;
            }

            Log.i("QR Scanner", "Prepare images");

            @SuppressLint("UnsafeOptInUsageError")
            Image mediaImage = imageProxy.getImage();
            if (mediaImage != null) {
                InputImage image = InputImage.fromMediaImage(mediaImage,
                        imageProxy.getImageInfo().getRotationDegrees());

                isProcessing = true;

                Log.i("QR Scanner", "Process image");

                Task<List<Barcode>> result = scanner.process(image)
                        .addOnSuccessListener(new OnSuccessListener<List<Barcode>>() {
                            @Override
                            public void onSuccess(List<Barcode> barcodes) {
                                // We assume that users scan only 1 QR code
                                // If we want to process on type of QR and multiple codes
                                // follow this guideline
                                // https://developers.google.com/ml-kit/vision/barcode-scanning/android#5.-get-information-from-barcodes

                                if (!barcodes.isEmpty()) {
                                    Barcode barcode = barcodes.get(0);
                                    String content = barcode.getRawValue();
                                    runOnUiThread(() -> showResult(content));
                                }
                                else {
                                    isProcessing = false;
                                    imageProxy.close();
                                }
                            }
                        })
                        .addOnCompleteListener(task -> {
                            imageProxy.close();
                        })
                        .addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.e("QR Scanner", e.toString());
                                isProcessing = false;
                            }
                        });
            }
            else {
                imageProxy.close();
            }
        });

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
    }

    private void parseInfo(String content) {
        // We can implement this function with the aim to parse the essential info
        // e.g Our application only support the QR codes generated by our service
        // If a QR code is not supported, the dialog closed and continue scanning
    }

    private void showResult(String result) {
        new AlertDialog.Builder(this)
                .setTitle("QR Detected")
                .setMessage(result)
                .setCancelable(false)
                .setPositiveButton("OK", (dialog, which) -> {
                    dialog.dismiss();
                    this.onBackPressed();
                    // scanning continues automatically
                })
                .show();
    }

    private void closeCamera() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        if (cameraExecutor != null && !cameraExecutor.isShutdown()) {
            cameraExecutor.shutdown();
        }
    }

    @Override
    public void onBackPressed() {
        closeCamera();
        super.onBackPressed();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        closeCamera();
    }
}