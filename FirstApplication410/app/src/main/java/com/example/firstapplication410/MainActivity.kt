package com.example.firstapplication410

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
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
import org.opencv.features2d.BRISK
import org.opencv.features2d.DescriptorMatcher
import org.opencv.features2d.Features2d.drawMatches
import org.opencv.features2d.ORB
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import org.opencv.imgproc.Imgproc.line
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.*


class MainActivity : AppCompatActivity() {

    var TAG = "MainActivity"

    private val SELECT_PHOTO_1 = 1
    private val SELECT_PHOTO_2 = 2
    private val SELECT_PHOTO_3 = 3
    private val MAX_MATCHES = 50
    var ivImage1: ImageView? = null
    var tvKeyPointsObject1: TextView? = null
    var tvKeyPointsObject2:TextView? = null
    var tvKeyPointsMatches:TextView? = null
    var tvTime:TextView? = null
    var keypointsObject1:Int = 0
    var keypointsObject2:Int = 0
    var keypointMatches:Int = 0
    lateinit var src1: Mat
    lateinit var src2:Mat
    var src1Selected:Boolean = false
    var src2Selected:Boolean = false

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

        actionBar?.setDisplayHomeAsUpEnabled(true)
        ivImage1 = findViewById(R.id.ivImage1)
        tvKeyPointsObject1 = findViewById(R.id.tvKeyPointsObject1)
        tvKeyPointsObject2 = findViewById(R.id.tvKeyPointsObject2)
        tvKeyPointsMatches = findViewById(R.id.tvKeyPointsMatches)
        keypointsObject1 = -1
        keypointsObject2 = -1
        keypointMatches = -1
        tvTime = findViewById(R.id.tvTime)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var id:Int? = item.itemId

        if(id == R.id.action_load_first_image){
            val photoPickerIntent = Intent(Intent.ACTION_PICK)
            photoPickerIntent.type = "image/*"
            startActivityForResult(photoPickerIntent, SELECT_PHOTO_1)
            return true
        }else if(id == R.id.action_load_second_image){
            val photoPickerIntent = Intent(Intent.ACTION_PICK)
            photoPickerIntent.type = "image/*"
            startActivityForResult(photoPickerIntent, SELECT_PHOTO_2)
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when(requestCode){
            SELECT_PHOTO_1 ->
                if(resultCode == Activity.RESULT_OK){
                    try {
                        val imageUri: Uri? = data?.data
                        val imageStream: InputStream? = imageUri?.let { contentResolver.openInputStream(it) }       //.let allow to put the Uri?
                        val selectedImage = BitmapFactory.decodeStream(imageStream)
                        src1 = Mat(selectedImage.height, selectedImage.width, CvType.CV_8UC4)
                        Utils.bitmapToMat(selectedImage, src1)
                        src1Selected = true
                    }catch(e: FileNotFoundException){
                        e.printStackTrace()
                    }
                }
            SELECT_PHOTO_2 ->
                if(resultCode == Activity.RESULT_OK){
                    try {
                        val imageUri: Uri? = data?.data
                        val imageStream: InputStream? = imageUri?.let { contentResolver.openInputStream(it) }       //.let allow to put the Uri?
                        val selectedImage = BitmapFactory.decodeStream(imageStream)
                        src2 = Mat(selectedImage.height, selectedImage.width, CvType.CV_8UC4)
                        Utils.bitmapToMat(selectedImage, src2)
                        src2Selected = true
                    } catch (e: FileNotFoundException){
                        e.printStackTrace()
                    }
                }
        }
        Toast.makeText(this, src1Selected.toString() + " " + src2Selected.toString(), Toast.LENGTH_SHORT).show()

        if(src1Selected && src2Selected){
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
    }

    fun executeTask(): Bitmap{
        Log.d("MainActivity", "Execute");

        //var detector: BRISK
        var detector: BRISK
        var keypoints1: MatOfKeyPoint = MatOfKeyPoint()
        var keypoints2: MatOfKeyPoint = MatOfKeyPoint()
        var descriptors1: Mat = Mat()
        var descriptors2: Mat = Mat()
        var descriptorMatcher: DescriptorMatcher
        var matches = MatOfDMatch()

        detector = BRISK.create()
        descriptorMatcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING)

        detector.detect(src2, keypoints2)
        detector.detect(src1, keypoints1)
        keypointsObject1 = keypoints1.toList().size
        keypointsObject2 = keypoints2.toList().size

        detector.compute(src1, keypoints1, descriptors1)
        detector.compute(src2, keypoints2, descriptors2)

        descriptorMatcher.match(descriptors1, descriptors2, matches)


        //Sort the matches
        val listOfMatches: List<DMatch> = matches.toList()
        fun selector(match: DMatch): Float = match.distance
        listOfMatches.sortedBy { selector(it) }
        if(listOfMatches.size>MAX_MATCHES){
            matches.fromList(listOfMatches.subList(0,MAX_MATCHES));
        }
        keypointMatches = matches.toList().size


        var src3: Mat = Mat(src1.height(), src1.width(), CvType.CV_8UC4)
        drawMatches(src1, keypoints1, src2, keypoints2, matches, src3)

        /*val objList = LinkedList<Point>()
        val sceneList = LinkedList<Point>()
        for (i in listOfMatches.indices) {
            objList.addLast(keypoints1.toList().get(listOfMatches[i].queryIdx).pt)
            sceneList.addLast(keypoints2.toList().get(listOfMatches[i].trainIdx).pt)
        }

        val obj = MatOfPoint2f()
        obj.fromList(objList)
        val scene = MatOfPoint2f()
        scene.fromList(sceneList)
        val hg: Mat = Calib3d.findHomography(obj, scene)

        val obj_corners = Mat(4, 1, CvType.CV_32FC2)
        val scene_corners = Mat(4, 1, CvType.CV_32FC2)

        obj_corners.put(0, 0)
        obj_corners.put
        obj_corners.put(src1.cols(), 0)
        obj_corners.put(src1.cols(), src1.rows())
        obj_corners.put(0, src1.rows())

        Core.perspectiveTransform(obj_corners, scene_corners, hg)


        Imgproc.line(src3, Point(scene_corners[0, 0]), Point(scene_corners[1, 0]), Scalar(0.0, 255.0, 0.0), 4)
        Imgproc.line(src3, Point(scene_corners[1, 0]), Point(scene_corners[2, 0]), Scalar(0.0, 255.0, 0.0), 4)
        Imgproc.line(src3, Point(scene_corners[2, 0]), Point(scene_corners[3, 0]), Scalar(0.0, 255.0, 0.0), 4)
        Imgproc.line(src3, Point(scene_corners[3, 0]), Point(scene_corners[0, 0]), Scalar(0.0, 255.0, 0.0), 4)*/

        Log.d("MainActivity", CvType.typeToString(src3.type()))

        val image1 = Bitmap.createBitmap(src3.cols(), src3.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(src3, image1)
        Imgproc.cvtColor(src3, src3, Imgproc.COLOR_BGR2RGB)

        return image1
    }
}
