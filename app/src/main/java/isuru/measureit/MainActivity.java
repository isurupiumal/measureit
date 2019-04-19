package isuru.measureit;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;


public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private Button btnDistance;
    private Button btnArea;
    private Button btnClear;
    private TextureView textureView;
    Bitmap bitmap;
    Sensor accelerometer;
    Sensor magnometer;
    SensorManager sm;
    ImageProcess imageProcess = null;
    ArrayList<Double> distanceList; // list to save distances
    ArrayList<Double> angleList; // list to save in between angles
    Image image;


    //Check state orientation of output image
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static{
        ORIENTATIONS.append(Surface.ROTATION_0,90);
        ORIENTATIONS.append(Surface.ROTATION_90,0);
        ORIENTATIONS.append(Surface.ROTATION_180,270);
        ORIENTATIONS.append(Surface.ROTATION_270,180);
    }

    private String cameraId;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSessions;
    private CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private ImageReader imageReader;

    //Save to FILE
    private File file;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private boolean mFlashSupported;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;

    CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
            cameraDevice.close();
            cameraDevice=null;
        }
    };

    static {
        if (!OpenCVLoader.initDebug())
            Log.d("ERROR", "Unable to load OpenCV");
        else
            Log.d("SUCCESS", "OpenCV loaded");
    }

    BaseLoaderCallback mOpenCVCallBack = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    //your code
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0,
                MainActivity.this, mOpenCVCallBack)) {
            Log.e("TEST", "Cannot connect to OpenCV Manager");
        }

        distanceList = new ArrayList<Double>();
        angleList = new ArrayList<Double>();
        imageProcess = new ImageProcess();
        textureView = (TextureView)findViewById(R.id.textureView);
        //From Java 1.4 , you can use keyword 'assert' to check expression true or false
        assert textureView != null;
        textureView.setSurfaceTextureListener(textureListener);

        //initialise the sensors
        sm = (SensorManager)getSystemService(SENSOR_SERVICE);
        accelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnometer = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sm.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        sm.registerListener(this, magnometer, SensorManager.SENSOR_DELAY_NORMAL);



        btnDistance = (Button)findViewById(R.id.btnDistance);
        btnDistance.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openCameraIntent();
                //takePicture();
               measureDistance();
            }
        });


        btnArea = (Button)findViewById(R.id.btnArea);
        btnArea.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Area();
            }
        });

        btnClear = (Button)findViewById(R.id.clear);
        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                clearData();
            }
        });
    }


    /**
     * capture the image
     */
    private  void  openCameraIntent(){
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(intent,100);
        if(intent.resolveActivity(getPackageManager()) != null){
            //Create a file to store the image
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File

            }
            /*if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,intent.putExtra(MediaStore.EXTRA_OUTPUT,photoURI));                                                                                          "com.example.android.provider", photoFile);

                startActivityForResult(intent, 100);
            }*/
        }
        openCamera();
    }

    String imageFilePath;

    /**
     * save image
     * @return image file
     * @throws IOException
     */
    private File createImageFile() throws IOException {

        String imageFileName = "pic1";
        File storageDir =
                getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(imageFileName,".jpg", storageDir );

        imageFilePath = image.getAbsolutePath();
        return image;
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && requestCode==100) {
            try {
                InputStream stream = getContentResolver().openInputStream(data.getData());
                Bitmap bitmap = BitmapFactory.decodeStream(stream);

                this.bitmap = bitmap;
                File file = new File(Environment.getExternalStorageDirectory()+"//pic1.jpg");

                    FileOutputStream fOutputStream = new FileOutputStream(file);

                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fOutputStream);

                    fOutputStream.flush();
                    fOutputStream.close();

                    MediaStore.Images.Media.insertImage(getContentResolver(), file.getAbsolutePath(), file.getName(), file.getName());


                stream.close();



                if (!OpenCVLoader.initDebug()) {

                    OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mOpenCVCallBack);
                } else {


                    mOpenCVCallBack.onManagerConnected(LoaderCallbackInterface.SUCCESS);
                }

            } catch (Exception e) {

            }
        }

    }


    /**
     * create a camera preview
     */
    private void createCameraPreview() {
        try{
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert  texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(),imageDimension.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    if(cameraDevice == null)
                        return;
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(MainActivity.this, "Changed", Toast.LENGTH_SHORT).show();
                }
            },null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * update the camera preview
     */
    private void updatePreview() {
        if(cameraDevice == null)
            Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
        try{
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(),null,mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * display camera view on textureview
     */
    private void openCamera() {
        CameraManager manager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);
        try{
            cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            //Check realtime permission if run higher API 23
            if(ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            {
                ActivityCompat.requestPermissions(this,new String[]{
                        Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                },REQUEST_CAMERA_PERMISSION);
                return;
            }
            manager.openCamera(cameraId,stateCallback,null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == REQUEST_CAMERA_PERMISSION)
        {
            if(grantResults[0] != PackageManager.PERMISSION_GRANTED)
            {
                Toast.makeText(this, "You can't use camera without permission", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        sm.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        sm.registerListener(this, magnometer, SensorManager.SENSOR_DELAY_NORMAL);
        if(textureView.isAvailable())
            openCamera();
        else
            textureView.setSurfaceTextureListener(textureListener);

        if (!OpenCVLoader.initDebug()) {

            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mOpenCVCallBack);
        } else {


            mOpenCVCallBack.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    protected void onPause() {
        stopBackgroundThread();
        super.onPause();
        sm.unregisterListener(this);
    }

    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try{
            mBackgroundThread.join();
            mBackgroundThread= null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * measure the distance to the recognizer
     */
    public void measureDistance(){


        double width = imageProcess.findMarkerWidth();
        if(width==0.0){
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("")
                    .setMessage("Image can't recognize. Please try again")
                    .setCancelable(false)
                    .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    }).show();
        }
        else{
            double distance =imageProcess.distanceToImage(102.047244,4961 ,width);

            if(angleList.size()>=1){
                angleList.add((double)azimuth);
            }else{
                angleList.add(0.0);
            }
            distance = Math.round(distance *1000000.0)/1000000.0;
            distanceList.add(distance);

            TextView tvDistance = (TextView)findViewById(R.id.textView);
            tvDistance.setText("Distance: "+distance);
        }

    }

    /**
     * calculate the area of the land using values in the arraylist
     * @return area
     */
    public double Area() {

        if (angleList.size() > 1) {
            double area = 0;
            for (int i = 1; i < distanceList.size() - 1; i++) {

                if(angleList.get(i-1)>angleList.get(i)){
                    angleList.set(i,(360.0+angleList.get(i)));
                }
                area = area + ((distanceList.get(i-1) * distanceList.get(i) / 2) * Math.sin(angleList.get(i) - angleList.get(i - 1)));
                area = Math.round(area*1000000.0)/1000000.0;


            }
            TextView tvArea = (TextView) findViewById(R.id.textView2);
            tvArea.setText("Area: " + area);
            return area;
        }
        else{
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("")
                    .setMessage("Select more than 1 point")
                    .setCancelable(false)
                    .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    }).show();
        }
        return 0.0;

    }

    /**
     * clear whole data
     */
    public void clearData(){
        distanceList.clear();
        distanceList = new ArrayList<Double>();
        angleList.clear();
        angleList = new ArrayList<Double>();
        TextView tvArea = (TextView)findViewById(R.id.textView2);
        tvArea.setText("Area: 0.0");
        TextView tvDistance = (TextView)findViewById(R.id.textView);
        tvDistance.setText("Distance: 0.0");
    }

    float Rot[]=null; //for gravity rotational data
    //don't use R because android uses that for other stuff
    float I[]=null; //for magnetic rotational data
    float accels[]=new float[3];
    float mags[]=new float[3];
    float[] values = new float[3];

    float azimuth;

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType())
        {
            case Sensor.TYPE_MAGNETIC_FIELD:
                mags = event.values.clone();
                break;
            case Sensor.TYPE_ACCELEROMETER:
                accels = event.values.clone();
                break;
        }

        if (mags != null && accels != null) {
            Rot = new float[9];
            I= new float[9];
            SensorManager.getRotationMatrix(Rot, I, accels, mags);
            // Correct if screen is in Landscape

            float[] outR = new float[9];
            SensorManager.remapCoordinateSystem(Rot, SensorManager.AXIS_X,SensorManager.AXIS_Z, outR);
            SensorManager.getOrientation(outR, values);

            azimuth = values[0] * 57.2957795f;

            mags = null;
            accels = null;
            if(azimuth<0){
                azimuth = (180 + azimuth )+180; //to avoid negative values
            }
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }


}
