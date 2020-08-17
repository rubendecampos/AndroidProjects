package com.example.houghobjectdetector

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import kotlin.math.atan2
import kotlin.math.roundToInt


class MainActivity : AppCompatActivity() {

    var TAG = "MainActivity"

    private val N_LINES = 50
    var ivImage1: ImageView? = null
    var text1: TextView? = null
    var text2: TextView? = null
    var text3: TextView? = null
    var text4: TextView? = null
    lateinit var grayMat: Mat

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
        var bMap: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.aiguille2)
        grayMat = Mat(bMap.height, bMap.width, CvType.CV_8UC4)
        Utils.bitmapToMat(bMap, grayMat)
        Imgproc.cvtColor(grayMat, grayMat, Imgproc.COLOR_BGR2GRAY)

        //------------------------------------------------------------------------------------
        //Bluring the images for the DoG method (DifferenceOfGaussian)
        //------------------------------------------------------------------------------------
        var blur1 = Mat(grayMat.height(),grayMat.width(),CvType.CV_8UC4)
        var blur2 = Mat(grayMat.height(),grayMat.width(),CvType.CV_8UC4)
        Imgproc.GaussianBlur(grayMat, blur1, Size(15.0,15.0), 5.0)
        Imgproc.GaussianBlur(grayMat, blur2, Size(21.0,21.0), 5.0)

        //DoG
        var dog = Mat(grayMat.height(),grayMat.width(),CvType.CV_8UC4)
        Core.absdiff(blur1,blur2,dog)

        //Threshold operation
        Core.multiply(dog, Scalar(50.0), dog)
        //Imgproc.threshold(dog, dog, 175.0, 255.0, Imgproc.THRESH_BINARY_INV)

        //------------------------------------------------------------------------------------
        //Edge detection using CannyEdge
        //------------------------------------------------------------------------------------
        var canny = Mat(grayMat.height(),grayMat.width(),CvType.CV_8UC4)
        Imgproc.Canny(grayMat, canny, 175.0, 150.0,3)
        //The edges resulted are too thin to be detected, so I dilate the canny Mat
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0,3.0))
        Imgproc.dilate(canny,canny,kernel)

        //Imgproc.threshold(grayMat, canny,175.0,250.0,Imgproc.THRESH_BINARY_INV)

        //------------------------------------------------------------------------------------
        //Circle detection using Hough transformation
        //------------------------------------------------------------------------------------
        var matOfCircles = Mat()
        Imgproc.HoughCircles(canny,matOfCircles,Imgproc.CV_HOUGH_GRADIENT,1.0,
            canny.rows()/4.0,20.0,20.0,0,canny.rows()/20)

        var houghTransform = Mat(grayMat.height(), grayMat.width(), CvType.CV_8UC4)
        Utils.bitmapToMat(bMap, houghTransform)

        //List of all the center and radius
        var circlesList = LinkedList<DoubleArray>()

        //Get the circles and radius from the mat of circles
        for (i in 0 until matOfCircles.cols()) {
            circlesList.addLast(matOfCircles.get(0, i))

            //Draw the circles
            val center = Point(circlesList.get(i)[0], circlesList.get(i)[1])
            val radius = circlesList.get(i)[2]
            Imgproc.circle(
                houghTransform, center, radius.toInt(),
                Scalar(0.0, 0.0, 255.0), 10
            )
        }

        //Get the center and radius of the best circle
        //We presume that the best circle is the one in the middle of the picture
        val middleCircle =
            getMiddleCircle(circlesList.toMutableList(), grayMat.height(), grayMat.width())

        //Draw the best circle
        val center = Point(middleCircle[0], middleCircle[1])
        val radius = middleCircle[2]
        Imgproc.circle(houghTransform, center, radius.toInt(),
            Scalar(255.0, 0.0, 0.0), 10)

        //------------------------------------------------------------------------------------
        //Line detection using Hough transformation
        //------------------------------------------------------------------------------------
        var matOfLines = Mat()
        //The lines must be at least 4 time longer than the radius of the circle
        Imgproc.HoughLinesP(canny,matOfLines,1.0,Math.PI/180,
            50,radius*3.0,100.0)

        //List of all the lines.
        //The elements of each array are in this order: x1,y1,x2,y2
        var linesList = LinkedList<DoubleArray>()

        //Get all the lines from the mat of lines
        for (i in 0 until matOfLines.rows()) {
            linesList.addLast(matOfLines.get(i,0))
        }

        //Sort the lines by their length and get only the first 4 lines (or less)
        var lines: List<DoubleArray>
        fun selector(id: DoubleArray): Double = distance2Points(Point(id[0],id[1]),
            Point(id[2],id[3]))
        val sortedLines = linesList.sortedByDescending{ selector(it) }

        //Remove the bad lines (lines with an angle significantly different from the angle
        //with the center)
        val tempList = removeBadLines(sortedLines,center)

        if(tempList.size>N_LINES){
            lines = tempList.subList(0,N_LINES).toList()
        }else{
            lines = tempList
        }

        //Log.d the sorted list
        /*for(i in 0 until sortedLines.size){
            var distance = distance2Points(Point(sortedLines.get(i)[0],sortedLines.get(i)[1]),
                Point(sortedLines.get(i)[2],sortedLines.get(i)[3]))
            var distanceTemp = distance2Points(Point(tempList.get(i)[0],tempList.get(i)[1]),
                Point(tempList.get(i)[2],tempList.get(i)[3]))
            Log.d("MainActivity","DISTANCE SORTED:" + distance.toString())
            Log.d("MainActivity","DISTANCE TEMP:" + distanceTemp.toString())
        }*/

        //Draw only the N longest lines
        for(i in 0 until lines.size){
            val pt1 = Point(lines.get(i)[0],lines.get(i)[1])
            val pt2 = Point(lines.get(i)[2],lines.get(i)[3])
            //Drawing lines on an image
            Imgproc.line(houghTransform, pt1, pt2, Scalar(0.0, 255.0, 0.0), 10)
        }

        //Find the furthest point and assume that it is the head of a clock hand
        var furthestPoint = Point()
        var maxDistance = 0.0
        for(i in 0 until lines.size){
            var pt1 = Point(lines.get(i)[0],lines.get(i)[1])
            var pt2 = Point(lines.get(i)[2],lines.get(i)[3])
            var distance1 = distance2Points(center,pt1)
            var distance2 = distance2Points(center,pt2)
            if(distance1 > distance2){
                if(distance1 > maxDistance){
                    maxDistance = distance1
                    furthestPoint = pt1
                }
            }else{
                if(distance2 > maxDistance){
                    maxDistance = distance2
                    furthestPoint = pt2
                }
            }
        }

        //Draw a line from the center to the furthest point and write the angle
        Imgproc.line(houghTransform, center, furthestPoint,
            Scalar(255.0, 0.0, 0.0), 10)
        var handAngle = angleClockwise(center,furthestPoint)
        var midLine = Point((furthestPoint.x-center.x)/2 + center.x,
            (furthestPoint.y-center.y)/2 + center.y)
        Imgproc.putText(houghTransform, handAngle.roundToInt().toString(), midLine, Imgproc.FONT_HERSHEY_COMPLEX,
            4.0, Scalar(255.0, 0.0, 0.0), 3)


        //------------------------------------------------------------------------------------
        //Display the result
        //------------------------------------------------------------------------------------
        Imgproc.cvtColor(houghTransform, houghTransform, Imgproc.COLOR_BGR2RGB)
        Imgproc.cvtColor(houghTransform, houghTransform, Imgproc.COLOR_RGB2BGR)
        Utils.matToBitmap(houghTransform, bMap)
        ivImage1!!.setImageBitmap(bMap)
        tvText1!!.text = "Detected circles : " + circlesList.size.toString()
        tvText2!!.text = "Detected lines : " + linesList.size.toString()
        tvText3!!.text = "Detected good lines : " + lines.size.toString()
        tvText4!!.text = ""

    }

    //Return the distance between 2 points
    fun distance2Points(pt1:Point, pt2:Point): Double{
        return Math.sqrt(Math.pow((pt2.x-pt1.x),2.0) + Math.pow((pt2.y-pt1.y),2.0))
    }

    //Remove the bad lines from the list
    //A bad line is a line where the angle between the line itself and the line
    //from the center to the furthest point is superior to a certain value
    fun removeBadLines(list: List<DoubleArray>, center:Point): MutableList<DoubleArray>{
        var resultList = LinkedList<DoubleArray>()

        for(i in 0 until list.size){
            val pt1 = Point(list.get(i)[0],list.get(i)[1])
            val pt2 = Point(list.get(i)[2],list.get(i)[3])
            var angleCenter = 0.0
            var angleLine = 0.0

            //which point is the furthest from the center?
            if(distance2Points(center,pt1)>distance2Points(center,pt2)){
                angleLine = angleClockwise(pt2,pt1)
                angleCenter = angleClockwise(center,pt1)
            }else{
                angleLine = angleClockwise(pt1,pt2)
                angleCenter = angleClockwise(center,pt2)
            }

            //Calculate the difference
            //If the difference  is greater than 180°, we do an
            //addition instead of a subtraction
            var diff = Math.abs(angleCenter-angleLine)
            if(diff > 180.0){
                if(angleCenter>angleLine){
                    diff = 360-angleCenter + angleLine
                }else{
                    diff = 360-angleLine + angleCenter
                }
            }

            //difference lower than 20° -> good line!
            if(diff < 20.0){
                resultList.addLast(list.get(i))
            }
        }

        return resultList.toMutableList()
    }

    //Calculate the angle of a line
    fun angleClockwise(center: Point, edge: Point): Double{
        //Calculate the angle for the vector center-edge
        val theta = atan2(edge.y-center.y,edge.x-center.x)
        var angle = theta*(180.0/3.141592)
        //atan2 give an angle between -180 and 180, we convert that
        if(angle<0) angle += 360.0
        //Then, because the [0;0] is in the top left corner of the screen
        //we readjust the angle (our 0° angle is at the vertical)
        angle = (angle + 90)%360

        return angle
    }

    //Return the closest circle from the center of a rectangle given by two Int
    fun getMiddleCircle(circles: MutableList<DoubleArray>, height: Int, width: Int): DoubleArray{
        var center = Point(width/2.0,height/2.0)
        //Get the first circle and first distance
        var circle = circles.get(0)
        var minDistance = distance2Points(Point(circle[0],circle[1]),center)

        //Go throw all circles and get the minDistance
        for(i in 0 until circles.size){
            var tempCircle = circles.get(i)
            var tempDistance = distance2Points(Point(tempCircle[0],tempCircle[1]),center)

            if(tempDistance<minDistance){
                minDistance = tempDistance
                circle = tempCircle
            }
        }

        return circle
    }
}
