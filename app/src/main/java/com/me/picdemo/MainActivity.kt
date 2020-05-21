package com.me.picdemo

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import com.me.picdemo.databinding.ActivityMainBinding
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.GPUImageAddBlendFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageAlphaBlendFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageTwoInputFilter
import kotlinx.android.synthetic.main.activity_main.*
import wseemann.media.FFmpegMediaMetadataRetriever
import java.io.File


class MainActivity : AppCompatActivity() {
    lateinit  var file:File
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var binding:ActivityMainBinding =DataBindingUtil.setContentView(this,R.layout.activity_main)
        val viewModel = ViewModelProvider(this).get(PicViewModel::class.java).apply {
            setdestImageView(binding.imageView3)
            setdestGPUImageView(binding.gpuimageview)
        }
        binding.lottie.apply{
            visibility = View.GONE
            frame
        }.imageAssetsFolder ="images"

        checkPermission(this)


         file =File(filesDir.absolutePath,"mvcolor.mp4").apply {
            if(this.exists()){
                delete()
                createNewFile()
               var inputStream= assets.open("mvcolor.mp4")
               var outputStream= outputStream()
                       try {
                           inputStream .copyTo(outputStream)
                       }finally {
                           inputStream.close()
                           outputStream.close()

                       }

            }

        }



        binding.data=viewModel
        binding.setLifecycleOwner(this)

    }

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
//                        Manifest.permission.ACCESS_COARSE_LOCATION,
//                        Manifest.permission.ACCESS_FINE_LOCATION,
//                        Manifest.permission.ACCESS_WIFI_STATE,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    102
                )
            }
        }
        return isGranted
    }
    fun getvideoinfo(view: View){



//        mmr.setDataSource("/storage/emulated/0/年兽大作战BD1280高清国语中英双字.MP4")

//        mmr.setDataSource("/storage/emulated/0/mvmask.mp4")
//        var mmr =FFmpegMediaMetadataRetriever()
        var mmr = MediaMetadataRetriever()
        mmr.setDataSource("/storage/emulated/0/mvcolor.mp4")
//        var meta_DURATION =mmr.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION)
//        var meta_FRAME_COUNT =mmr.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_FRAMERATE)
        var meta_DURATION =mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        var meta_FRAME_COUNT =mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
        Log.e("ok","meta_DURATION is ${meta_DURATION}")
        Log.e("ok","meta_FRAME_COUNT is ${meta_FRAME_COUNT}")
        val b_color = mmr.getFrameAtTime() // frame at 2 seconds
        mmr.setDataSource("/storage/emulated/0/mvmask.mp4")
        val b_mask = mmr.getFrameAtTime() // frame at 2 seconds

        var bitmap = GPUImage(this)
        bitmap.setImage(b_color)
        var filter = GPUImageTwoInputFilter(PicViewModel.TEST_ALPHA_FRAGMENT_SHADER)
        filter.bitmap =  b_mask
        bitmap.setFilter(filter)
        val result =bitmap.bitmapWithFilterApplied
        bitmap.setImage(BitmapFactory.decodeStream(assets.open("images/2-1.png")))
        var b_filter = GPUImageAlphaBlendFilter()
        b_filter.bitmap =  result
        bitmap.setFilter(b_filter)
        val b_result =bitmap.bitmapWithFilterApplied
//        mmr.geth
        imageView3.setImageBitmap(b_result)

        MediaUtils().getMediaCodecList()
//        MediaMuxer("/storage/emulated/0/mvresult.mp4",MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
//        Log.e("ok","meata is ${meta.toString()}")

    }





}