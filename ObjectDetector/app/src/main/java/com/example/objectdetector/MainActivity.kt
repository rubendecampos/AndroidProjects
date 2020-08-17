package com.example.objectdetector

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.*
import org.opencv.features2d.*
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import org.opencv.imgproc.Imgproc.*
import java.util.*
import kotlin.math.absoluteValue
import kotlin.math.atan2
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    var TAG = "MainActivity"

    private val MAX_MATCHES = 50
    var ivImage1: ImageView? = null
    var tvKeyPointsObject1: TextView? = null
    var tvKeyPointsObject2:TextView? = null
    var tvKeyPointsMatches:TextView? = null
    var tvTime:TextView? = null
    var keypointsObject1:Int = 0
    var keypointsObject2:Int = 0
    var keypointMatches:Int = 0
    lateinit var obj: Mat
    lateinit var scene: Mat
    lateinit var src: Mat

    val mOpenCVCallBack: BaseLoaderCallback = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) {
            when (status) {
                LoaderCallbackInterface.SUCCESS ->                     //DO YOUR WORK/STUFF HERE
                    System.loadLibrary("nonfree")
                else -> super.onManagerConnected(status)
            }
        }
    }

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
        tvKeyPointsObject1 = findViewById(R.id.tvKeyPointsObject1)
        tvKeyPointsObject2 = findViewById(R.id.tvKeyPointsObject2)
        tvKeyPointsMatches = findViewById(R.id.tvKeyPointsMatches)
        keypointsObject1 = -1
        keypointsObject2 = -1
        keypointMatches = -1
        tvTime = findViewById(R.id.tvTime)

        //Load images
        var bMap: Bitmap =
        BitmapFactory.decodeResource(resources, R.drawable.aiguille)
        obj = Mat(bMap.height, bMap.width, CvType.CV_8UC4)
        cvtColor(obj, obj, Imgproc.COLOR_BGR2GRAY)
        Utils.bitmapToMat(bMap, obj)
        bMap = BitmapFactory.decodeResource(resources, R.drawable.aiguille_scene)
        scene = Mat(bMap.height, bMap.width, CvType.CV_8UC4)
        cvtColor(scene, scene, Imgproc.COLOR_BGR2GRAY)
        Utils.bitmapToMat(bMap, scene)

        //enable task
        Log.d("MainActivity", "Before Execute")
        object : AsyncTask<Void?, Void?, Bitmap?>() {
            private var startTime: Long = 0
            private var endTime: Long = 0

            override fun onPreExecute() {
                super.onPreExecute()
                startTime = System.currentTimeMillis()
            }

            override fun onPostExecute(bitmap: Bitmap?) {
                super.onPostExecute(bitmap)
                endTime = System.currentTimeMillis()
                ivImage1!!.setImageBitmap(bitmap)
                tvKeyPointsObject1!!.text = "Object 1 : $keypointsObject1"
                tvKeyPointsObject2!!.text = "Object 2 : $keypointsObject2"
                tvKeyPointsMatches!!.text = "Keypoint Matches : $keypointMatches"
                tvTime!!.text = "Time taken : " + (endTime - startTime) + " ms"
            }

            override fun doInBackground(vararg params: Void?): Bitmap? {
                return executeTask()
            }
        }.execute()
    }

    fun executeTask(): Bitmap{
        Log.d("MainActivity", "Execute")

        //var detector: ORB
        var detector: ORB

        var keypoints1: MatOfKeyPoint = MatOfKeyPoint()
        var keypoints2: MatOfKeyPoint = MatOfKeyPoint()
        var descriptors1: Mat = Mat()
        var descriptors2: Mat = Mat()
        var descriptorMatcher: DescriptorMatcher
        var matches = MatOfDMatch()

        detector = ORB.create()
        descriptorMatcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING)

        detector.detect(scene, keypoints2)
        detector.detect(obj, keypoints1)
        keypointsObject1 = keypoints1.toList().size
        keypointsObject2 = keypoints2.toList().size

        detector.compute(obj, keypoints1, descriptors1)
        detector.compute(scene, keypoints2, descriptors2)

        descriptorMatcher.match(descriptors1, descriptors2, matches)


        //Sort the matches
        val tempList: List<DMatch> = matches.toList()
        fun selector(match: DMatch): Float = match.distance
        val listOfMatches = tempList.sortedBy { selector(it) }
        if(listOfMatches.size>MAX_MATCHES){
            matches.fromList(listOfMatches.subList(0,MAX_MATCHES));
        }
        keypointMatches = matches.toList().size

        val objList = LinkedList<Point>()
        val sceneList = LinkedList<Point>()
        for (i in listOfMatches.indices) {
            objList.addLast(keypoints1.toList().get(listOfMatches[i].queryIdx).pt)
            sceneList.addLast(keypoints2.toList().get(listOfMatches[i].trainIdx).pt)
        }

        val objPoint = MatOfPoint2f()
        objPoint.fromList(objList)
        val scenePoint = MatOfPoint2f()
        scenePoint.fromList(sceneList)

        //Find the homography in the two MatOfPoint2f
        val hg: Mat = Calib3d.findHomography(objPoint, scenePoint, Calib3d.RANSAC, 5.0)

        //type need to be CV_32FC2 <-- 2 because there are two arguments (x,y)
        val objCorners = Mat(6,1,CvType.CV_32FC2)
        val sceneCorners = Mat(4,1,CvType.CV_32FC2)
        //Put the corners of the object int the Mat variable
        objCorners.put(0,0, 0.0,0.0)
        objCorners.put(1,0, obj.cols().toDouble(),0.0)
        objCorners.put(2,0, obj.cols().toDouble(),obj.rows().toDouble())
        objCorners.put(3,0, 0.0,obj.rows().toDouble())
        objCorners.put(4,0,obj.cols().toDouble()/2,0.0)
        objCorners.put(5,0,obj.cols().toDouble()/2,obj.rows().toDouble())

        Core.perspectiveTransform(objCorners, sceneCorners, hg)

        //Draw the line around the object
        cvtColor(scene, scene, COLOR_BGR2RGB)
        cvtColor(scene, scene, COLOR_RGB2BGR)
        val topLeft = Point(sceneCorners[0,0])
        val topRight = Point(sceneCorners[1,0])
        val botRight = Point(sceneCorners[2,0])
        val botLeft = Point(sceneCorners[3,0])
        val topMid = Point(sceneCorners[4,0])
        val botMid = Point(sceneCorners[5,0])
        /*line(scene, topLeft, topRight, Scalar(255.0, 0.0, 0.0), 10)
        line(scene, topRight, botRight, Scalar(255.0, 0.0, 0.0), 10)
        line(scene, botRight, botLeft, Scalar(255.0, 0.0, 0.0), 10)
        line(scene, botLeft, topLeft, Scalar(255.0, 0.0, 0.0), 10)
        line(scene, botMid, topMid, Scalar(255.0, 0.0, 0.0), 20)*/
        //calculate and display the angle for two lines
        val midLine1 = Point((botRight.x-topRight.x)/2 + topRight.x,
            (botRight.y-topRight.y)/2 + topRight.y)
        val midLine2 = Point((botLeft.x-topLeft.x)/2 + topLeft.x,
            (botLeft.y-topLeft.y)/2 + topLeft.y)
        val midLine3 = Point((botMid.x-topMid.x)/2 + topMid.x,
            (botMid.y-topMid.y)/2 + topMid.y)
        var angle = angleClockwise(botRight, topRight)
        /*putText(scene,angle.roundToInt().toString(),midLine1, FONT_HERSHEY_COMPLEX,
            4.0,Scalar(255.0,0.0,0.0),3)
        angle = angleClockwise(botLeft, topLeft)
        putText(scene,angle.roundToInt().toString(),midLine2, FONT_HERSHEY_COMPLEX,
            4.0,Scalar(255.0,0.0,0.0),3)
        angle = angleClockwise(botMid, topMid)
        putText(scene,angle.roundToInt().toString(),midLine3, FONT_HERSHEY_COMPLEX,
            4.0,Scalar(255.0,0.0,0.0),5)*/
        //Draw the matches
        var src3 = Mat(obj.height(), obj.width(), CvType.CV_8UC4)
        Features2d.drawMatches(obj, keypoints1, scene, keypoints2, matches, src3)
        //Features2d.drawKeypoints(obj, keypoints1,src3)

        Log.d("MainActivity", CvType.typeToString(src3.type()))

        val image1 = Bitmap.createBitmap(src3.cols(), src3.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(src3, image1)

        return image1
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

}
