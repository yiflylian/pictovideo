package com.me.picdemo

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import com.me.picdemo.databinding.ActivityMainBinding
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.GPUImageAlphaBlendFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageTwoInputFilter
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONObject
import wseemann.media.FFmpegMediaMetadataRetriever
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.Charset


class MainActivity : AppCompatActivity() {
    lateinit  var file:File
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var binding:ActivityMainBinding =DataBindingUtil.setContentView(this,R.layout.activity_main)
        val viewModel = ViewModelProvider(this).get(PicViewModel::class.java).apply {
            setdestImageView(binding.imageView3)
        }
        binding.lottie.apply{
//            visibility = View.GONE
//            frame
        }.imageAssetsFolder ="images"

        checkPermission(this)


        binding.data=viewModel
        binding.setLifecycleOwner(this)

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


    fun getvideoinfo(view: View){




//        mmr.setDataSource("/storage/emulated/0/年兽大作战BD1280高清国语中英双字.MP4")
//        mmr.setDataSource("/storage/emulated/0/mvmask.mp4")
//        var mmr = FFmpegMediaMetadataRetriever()
//        var meta_DURATION =mmr.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION)
//        var meta_FRAME_COUNT =mmr.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_FRAMERATE)

        var mmr_color = FFmpegMediaMetadataRetriever()
        mmr_color.setDataSource("/storage/emulated/0/mvColor.mp4")
        var meta_color_DURATION =mmr_color.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION)
        var  meta_color_FRAMERATE= mmr_color.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_FRAMERATE).toInt()
        var  meta_color_FRAME_COUNT =mmr_color.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_FRAMERATE).toFloat()*(meta_color_DURATION.toFloat()/1000).toInt()

        Log.e("ok","meta_color_DURATION is ${meta_color_DURATION}")
        Log.e("ok","meta_color_FRAMERATE is ${meta_color_FRAMERATE}")
        Log.e("ok","meta_color_FRAME_COUNT is ${meta_color_FRAME_COUNT}")

        var mmr_mask = FFmpegMediaMetadataRetriever()
        mmr_mask.setDataSource("/storage/emulated/0/mvMask.mp4")
        var meta_mask_DURATION =mmr_mask.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION)
        var meta_mask_FRAME_COUNT =mmr_mask.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_FRAMERATE).toFloat()*(meta_mask_DURATION.toFloat()/1000).toInt()

        Log.e("ok","meta_mak_DURATION is ${meta_mask_DURATION}")
        Log.e("ok","meta_mak_FRAME_COUNT is ${meta_mask_FRAME_COUNT}")


        var bitmap = GPUImage(this)
          var json_data= String(assets.open("data.json").readBytes(), Charset.defaultCharset())
//        Log.e("ok","${json_data}")
        var op =JSONObject(json_data).getInt("op")
       var w= JSONObject(json_data).getInt("w")
       var h= JSONObject(json_data).getInt("h")
        Log.e("ok"," op  is  ${op}")
        var video_format :MediaFormat?=null
        var  mDecodeTrackIndex:Int =0
        var mMimeType:String=""
         MediaExtractor().apply {

            try {
                setDataSource("/storage/emulated/0/mvColor.mp4")
                val trackCount: Int = getTrackCount()
                for (i in 0 until trackCount) {
                    val format: MediaFormat =getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME)
                    if (mime.startsWith("video/")) {
                        video_format = format
                        mDecodeTrackIndex = i
                        mMimeType = mime
                        break
                    }
                }

            } catch (e: IOException) {
                release()
            }

        }
        var mMuxer = MediaMuxer("/storage/emulated/0/result.mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        if(video_format==null){
            Log.e("ok","video_format is  null")
            return
        }



        var info = MediaCodec.BufferInfo()
        var encoder = MediaCodec.createEncoderByType(mMimeType);
        encoder.configure(video_format,null,null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        mMuxer.addTrack(video_format!!)
        mMuxer.start()


//        val b_color_2 =  mmr_color.getFrameAtTime(2*1000000,FFmpegMediaMetadataRetriever.OPTION_CLOSEST)
//        val b_color_10000 =  mmr_color.getFrameAtTime(9*1000000,FFmpegMediaMetadataRetriever.OPTION_CLOSEST)
//
//        bitmap.saveToPictures(b_color_2,"reulst/test","b_color_2.jpg",GPUImage.OnPictureSavedListener {
//
//        })
//        bitmap.saveToPictures(b_color_10000,"reulst/test","b_color_10000.jpg",GPUImage.OnPictureSavedListener {
//
//        })
//
//
//        return

        for (i in 0 until  op){
            lottie.frame =i
            val  b_back =loadBitmapFromView(lottie,w,h)

            var b_color =  mmr_color.getFrameAtTime(((i/meta_color_FRAMERATE)*1000000L),FFmpegMediaMetadataRetriever.OPTION_CLOSEST)
            Log.e("ok"," b_color time -> ${(meta_color_DURATION.toLong()*1000000/meta_color_FRAME_COUNT.toLong()*i)}")
            if(b_color == null){
                Log.e("ok","b_color is  null")
                b_color =b_back
            }

            var b_mask =  mmr_mask.getFrameAtTime((i/meta_color_FRAMERATE)*1000000L,FFmpegMediaMetadataRetriever.OPTION_CLOSEST)
            Log.e("ok"," b_mask time -> ${(meta_mask_DURATION.toLong()*1000000/meta_mask_FRAME_COUNT.toLong()*i)}")
            if(b_mask == null){
                Log.e("ok","b_mask is  null")
                b_mask =b_back
            }


            bitmap.setImage(b_color)
            var filter = GPUImageTwoInputFilter(PicViewModel.TEST_ALPHA_FRAGMENT_SHADER)
            filter.bitmap =  b_mask
            bitmap.setFilter(filter)
            val result =bitmap.bitmapWithFilterApplied



            bitmap.setImage(b_back)

            var b_filter = GPUImageAlphaBlendFilter()
            b_filter.bitmap =  result
            bitmap.setFilter(b_filter)
            val b_result =bitmap.bitmapWithFilterApplied
            var data  =ByteBuffer.allocate(w*h)
            b_result.copyPixelsToBuffer(data)


            mMuxer.writeSampleData(mDecodeTrackIndex,data,info)
            if(i==op-1){
                mMuxer.stop()
            }


//            AvcExecuteAsyncTask.execute(provider, 16, handler, path);
            bitmap.saveToPictures(b_color,"reulst/color","color_${i}.jpg",GPUImage.OnPictureSavedListener {


            })
            bitmap.saveToPictures(b_mask,"reulst/mask","mask_${i}.jpg",GPUImage.OnPictureSavedListener {

            })
            bitmap.saveToPictures(b_back,"reulst/lottie","lottie_${i}.jpg",GPUImage.OnPictureSavedListener {

            })
            bitmap.saveToPictures(b_result,"reulst/result","result_${i}.jpg",GPUImage.OnPictureSavedListener {

            })
//            imageView3.setImageBitmap(b_result)
        }




    }


    private fun loadBitmapFromView(v: View,w:Int,h:Int): Bitmap? {
       var bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        var c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        /** 如果不设置canvas画布为白色，则生成透明  */
        v.layout(0, 0, w, h)
        v.draw(c)
//        imageView3.setImageBitmap(bmp)
        return bmp
    }



}
fun  main(){
    var meta_color_DURATION =  10000
    var meta_color_FRAME_COUNT = 150
   for (i in 0 until 150){
       println(meta_color_DURATION.toLong()/meta_color_FRAME_COUNT.toLong()*i)
   }

}