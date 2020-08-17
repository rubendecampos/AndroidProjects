package com.example.clockcenterdetection

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.solver.widgets.Rectangle
import kotlinx.android.synthetic.main.activity_main.*
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.util.*

class MainActivity : AppCompatActivity() {

    var TAG = "MainActivity"

    var ivImage1: ImageView? = null
    var text1: TextView? = null
    var text2: TextView? = null
    var text3: TextView? = null
    var text4: TextView? = null
    lateinit var grayMat: Mat
    lateinit var out: Mat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        var success = OpenCVLoader.initDebug()
        if(success){
            Log.d(TAG, "OpenCV init properly")
        }else{
            Log.e(TAG, "OpenCV init failed")
        }
        Toast.makeText(this, success.toString(), Toast.LENGTH_LONG).show()

        //init var
        ivImage1 = findViewById(R.id.ivImage1)
        text1 = findViewById(R.id.tvText1)
        text2 = findViewById(R.id.tvText2)
        text3 = findViewById(R.id.tvText3)
        text4 = findViewById(R.id.tvText4)

        //Load images
        var bMap: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.matrice)
        grayMat = Mat(bMap.height, bMap.width, CvType.CV_8UC4)
        Utils.bitmapToMat(bMap, grayMat)
        Imgproc.cvtColor(grayMat, grayMat, Imgproc.COLOR_BGR2GRAY)

        //------------------------------------------------------------------------------------
        //Edge detection using CannyEdge
        //------------------------------------------------------------------------------------
        var canny = Mat(grayMat.height(),grayMat.width(),CvType.CV_8UC4)
        Imgproc.Canny(grayMat, canny, 10.0, 100.0,3)
        //The edges resulted are too thin, so I dilate the canny Mat
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(8.0,8.0))
        Imgproc.dilate(canny,canny,kernel)

        //Out mat with the detected circle
        out = Mat(grayMat.height(), grayMat.width(), CvType.CV_8UC4)
        Utils.bitmapToMat(bMap, out)

        var threshold = Mat(grayMat.height(),grayMat.width(),CvType.CV_8UC4)
        Imgproc.threshold(grayMat, threshold,127.0,255.0,Imgproc.THRESH_BINARY_INV)
        //Imgproc.adaptiveThreshold(grayMat,threshold,255.0,
            //Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,Imgproc.THRESH_BINARY,11,2.0)

        //------------------------------------------------------------------------------------
        //Contours detection and circle detection
        //------------------------------------------------------------------------------------
        var contours:List<MatOfPoint> = ArrayList<MatOfPoint>()
        var hierarchy = Mat()
        var circles = LinkedList<DoubleArray>()
        Imgproc.findContours(threshold,contours,hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        findCirclesInContours(contours,circles)


        //------------------------------------------------------------------------------------
        //Display the result
        //------------------------------------------------------------------------------------
        Utils.matToBitmap(out, bMap)
        ivImage1!!.setImageBitmap(bMap)
        tvText1!!.text = "Detected contours : " + contours.size
        tvText2!!.text = "Detected circles : " + circles.size
        tvText3!!.text = ""
        tvText4!!.text = ""
    }


    //Find all circle in the contour list by comparing their area with the area of their
    //enclosing circle.
    //When a circle is found, another circle with a really close center isn't taken.
    fun findCirclesInContours(contours:List<MatOfPoint>,circles:LinkedList<DoubleArray>): Boolean{
        var radius = FloatArray(1)
        var center = Point()
        for(i in 0 until contours.size){
            //Get i-th contour and get his enclosing circle
            var contour = MatOfPoint2f()
            contours[i].convertTo(contour,CvType.CV_32F)
            Imgproc.minEnclosingCircle(contour,center,radius)

            //Calculate the area of the enclosing circle and get the actual area of the contour
            val area = Math.PI*radius[0]*radius[0]
            val contourArea = Imgproc.contourArea(contour)

            //Compare the area of the enclosing circle with the contour
            //If they are similar, the contour is a circle
            if(contourArea < area &&
                contourArea > area*0.7){
                val circle =  DoubleArray(3)
                circle.set(0,center.x)
                circle.set(1,center.y)
                circle.set(2,radius[0].toDouble())

                //is the center close to one a the founded circles center?
                var isClose = false
                for(i in 0 until circles.size){
                    if(circles[i][0]-circles[i][2] < circle[0] &&
                        circles[i][0]+circles[i][2] > circle[0] &&
                        circles[i][1]-circles[i][2] < circle[1] &&
                        circles[i][1]+circles[i][2] > circle[1]){
                        isClose = true
                    }
                }

                //If it is not close, add the circle to the list and draw it
                if(!isClose){
                    circles.addLast(circle)
                    Imgproc.circle(out,Point(circle[0],circle[1]),circle[2].toInt(),
                            Scalar(0.0,255.0,0.0),30)
                }
            }else{
                Imgproc.drawContours(out,contours,i, Scalar(255.0,0.0,0.0),5)
            }
        }

        return circles.size>0
    }
}
