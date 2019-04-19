
package isuru.measureit;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;


import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;


import java.io.File;
import java.util.ArrayList;
import java.util.List;



public class ImageProcess {

    private  int pic_count = 0;
    public double knownWidth = 0;
    public double focalLength = 102.047244;

    Bitmap bmp = null;

    public void getImage(Bitmap bmp){
        this.bmp = bmp;
    }

    public  double findMarkerWidth(){
        bmp = BitmapFactory.decodeFile(Environment.getExternalStorageDirectory()+"//pic1.jpg");
        Mat frame = new Mat();
        Bitmap Bitmap32 = bmp.copy(Bitmap.Config.ARGB_8888, true);
        Utils.bitmapToMat(Bitmap32,frame);

        Mat gscale = new Mat();
        Mat blur = new Mat();
        Mat edged = new Mat();

        // convert the image to grayscale, blur it, and detect edges
        if(frame.channels()>1)
            Imgproc.cvtColor(frame, gscale, Imgproc.COLOR_BGR2GRAY);
        else
            gscale = frame;

        Imgproc.GaussianBlur(gscale, blur, new Size(5, 5), 0);
        Imgproc.Canny(blur,edged,35.0,125.0);

        // find the contours in the edged image and keep the largest one;
        // we'll assume that this is our piece of paper in the image
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat(edged.width(), edged.height(), CvType.CV_8UC1);
        Imgproc.findContours(edged.clone(), contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
        int max_idx = 0;

        // if any contour exist...
        if (hierarchy.size().height > 0 && hierarchy.size().width > 0)
        {
            double max_area = 0;
            double area;
            // find the contour with largest area
            for (int idx = 0; idx >= 0; idx = (int) hierarchy.get(0, idx)[0])
            {
                area = Imgproc.contourArea(contours.get(idx));
                if(area > max_area){
                    max_area = area;
                    max_idx = idx;
                }
                Imgproc.drawContours(frame, contours, idx, new Scalar(0, 0, 255));
            }

            byte[] bytes = new byte[ frame.rows() * frame.cols() * frame.channels() ];


            File file = new File(Environment.getExternalStorageDirectory()+"/pic1.jpg");
            //pic_count++;

            Boolean bool = null;
            String filename = file.toString();
            bool = Imgcodecs.imwrite(filename, frame);

            //max_area;


        }


        if(contours.size() == 0){
            return  0;
        }else {

            MatOfPoint2f point = new MatOfPoint2f();
            contours.get(max_idx).convertTo(point, CvType.CV_32F);

            return Imgproc.arcLength(point, true);
        }

    }

    public double distanceToImage(double focalLength, double knownWidth, double pixelsPerWidth){
        return ((knownWidth * this.focalLength) / pixelsPerWidth)*0.0002645833;
    }
}
