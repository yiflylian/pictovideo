package com.me.picdemo

import android.Manifest
import android.R.attr.x
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.*
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.GPUImageView
import jp.co.cyberagent.android.gpuimage.filter.GPUImageTwoInputFilter
import java.io.File


/**
@author simpler
@create 2020年05月11日  10:04
 */
class PicViewModel : ViewModel() {

    companion object{
        val TEST_ALPHA_FRAGMENT_SHADER = "varying highp vec2 textureCoordinate;\n" +
                " varying highp vec2 textureCoordinate2;\n" +
                "\n" +
                " uniform sampler2D inputImageTexture;\n" +
                " uniform sampler2D inputImageTexture2;\n" +
                "\n" +
                " void main()\n" +
                " {\n" +
                "   lowp vec4 textureColor = texture2D(inputImageTexture, textureCoordinate);\n" +
                "   lowp vec4 textureColor2 = texture2D(inputImageTexture2, textureCoordinate2);\n" +
                "\n" +
                "   gl_FragColor = vec4(textureColor.rgb, textureColor.a * textureColor2.r);\n" +
                " }";
    }

    var pic: MutableLiveData<String> = MutableLiveData<String>().apply {
        value = "@drawable/mask"
    }

    lateinit var destImageView: ImageView
    lateinit var mGPUImageView: GPUImageView
    fun setdestImageView(destImageView: ImageView) {
        this.destImageView = destImageView
    }
    fun setdestGPUImageView(GPUImageView: GPUImageView) {
        this.mGPUImageView = GPUImageView
    }
    fun createPics(v:View) {

        var bitmap = GPUImage(destImageView.context)
        bitmap.setImage(getColorBitmap())
        var filter =GPUImageTwoInputFilter(TEST_ALPHA_FRAGMENT_SHADER)
        filter.bitmap =  getMaskBitmap()
        bitmap.setFilter(filter)
        val result =bitmap.bitmapWithFilterApplied
        destImageView.setImageBitmap(result)

        bitmap.saveToPictures(destImageView.context.filesDir.absolutePath,"result.png",GPUImage.OnPictureSavedListener {
            Log.e("ok "," save pic end")
        })
//        val root_path =destImageView.context.filesDir.absolutePath
//        File(root_path,"result.png").apply{
//            if(exists()){
//                delete()
//                createNewFile()
//            }
//            var out = outputStream()
//            result.compress(Bitmap.CompressFormat.PNG,90, out)
//            out.flush()
//            out.close()
//        }
    }



    //<editor-fold desc="权限检查">
    fun checkPermission(context: Activity): Boolean? {
        var isGranted = true
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            if (context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                //如果没有写sd卡权限
                isGranted = false
            }
            if (context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                isGranted = false
            }
            Log.e("appconfig", "isGranted == $isGranted")
            if (!isGranted) {
                context.requestPermissions(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    102
                )
            }
        }
        return isGranted
    }
    // </editor-fold>
    fun getColorBitmap()= BitmapFactory.decodeResource(destImageView.resources, R.drawable.colorimage90)
    fun getMaskBitmap()= BitmapFactory.decodeResource(destImageView.resources, R.drawable.maskimage90)

}
fun  main(){
    val BLACK = -16777216
    val BLUE = -16776961
    val CYAN = -16711681
    val DKGRAY = -12303292
    val GRAY = -7829368
    val GREEN = -16711936
    val LTGRAY = -3355444
    val MAGENTA = -65281
    val RED = -65536
    val TRANSPARENT = 0
    val WHITE = -1
    val YELLOW = -256
    println("WHITE and BLACK ${ WHITE and BLACK}") //
    println("TRANSPARENT and BLACK ${ TRANSPARENT and BLACK}") //
    println("GREEN and BLACK ${ GREEN and BLACK}") //
    println("LTGRAY and BLACK ${ LTGRAY and BLACK}") //
}