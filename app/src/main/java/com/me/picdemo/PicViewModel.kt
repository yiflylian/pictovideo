package com.me.picdemo

import android.R.attr.x
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

    fun createPic(View: View) {

        Log.e("ok", "createPic(View:View)")

        var backBitmap = BitmapFactory.decodeResource(destImageView.resources, R.drawable.colorimage90)
        var frontBitmap = BitmapFactory.decodeResource(destImageView.resources, R.drawable.maskimage90)
        var width = frontBitmap.width
        var height = frontBitmap.height
        var frontBitmap_array = IntArray(width * height, { 0 })
        var backBitmap_array = IntArray(width * height, { 0 })
        var destBitmap_array = IntArray(width * height, { 0 })

        backBitmap.getPixels(backBitmap_array, 0, width, 0, 0, width, height)
        frontBitmap.getPixels(frontBitmap_array, 0, width, 0, 0, width, height)
        var destBitmap02 = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val nlf = floatArrayOf(
            0f,0f,0f,0f, 0f,
            0f,0f,0f,0f,0f,
            0f,0f,0f,0f,0f,
            1f,0f,0f,0f,0f
        )


        var paint =Paint().apply {
            setColorFilter(ColorMatrixColorFilter(ColorMatrix(nlf)))
            setXfermode(PorterDuffXfermode(PorterDuff.Mode.DST_IN))
        }

        for (i in 0 until backBitmap_array.size - 1) {
//            pixels.get(x) =
//                pixels.get(x) and 0x00FFFFFF or (alpha.get(x) shl 8 and -0x1000000)
            destBitmap_array[i] = backBitmap_array[i] and  0x00FFFFFF or (frontBitmap_array[i] shl 8 and -0x1000000)
//            if (destBitmap_array[i]!= backBitmap_array[i]) {
//                destBitmap_array[i] = Color.TRANSPARENT
//            }

        }

        destBitmap02.setPixels(destBitmap_array, 0, width, 0, 0, width, height)
//        var canvas = Canvas(destBitmap02)
//        canvas.setDensity(Bitmap.DENSITY_NONE);
//
//        canvas.drawBitmap(destBitmap02,0F,0F,paint)

            destImageView.setImageBitmap(destBitmap02)
        mGPUImageView.setImage(destBitmap02)


        mGPUImageView.filter=   GPUImageTwoInputFilter(TEST_ALPHA_FRAGMENT_SHADER)
//        destImageView.setColorFilter(Color.GRAY)
        Log.e("ok", "createPic(View:View)  end")



        Log.e("ok", " filesDir.absolutePath  is ${destImageView.context.filesDir.absolutePath}")
        val root_path =destImageView.context.filesDir.absolutePath
        File(root_path,"result.png").apply{
            if(exists()){
                delete()
                createNewFile()
            }
           var out = outputStream()
          destBitmap02.compress(Bitmap.CompressFormat.PNG,90, out)
            out.flush()
            out.close()
        }

        return


    }

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