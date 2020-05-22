package com.me.picdemo

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.*
import android.media.MediaFormat.*
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
import xyz.mylib.creator.task.AvcExecuteAsyncTask
import java.io.File
import java.nio.ByteBuffer


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
        var bytes =ByteArray(b_result.getRowBytes() * b_result.getHeight())
        var  byteBuffer = ByteBuffer.wrap(bytes)
             b_result.copyPixelsFromBuffer(byteBuffer)
        imageView3.setImageBitmap(b_result)
       var MediaUtils = MediaUtils()
        MediaUtils .getMediaCodecList()
        var width =720
        var height=1280
        var  framerate =20
        var frame_interval =10
       val  TIME_OUT_US = 10000L
        Thread{
            //创建媒体格式
            var mediaFormat =MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,width,height)
           var  colorFormat= MediaUtils.getColorFormat()
            //设置视频的码率
            //1080P最好在5Mbps/5120Kbps到8Mbps/8192Kbps之间,因为低于5Mbps不够清晰,而大于8Mbps视频文件会过大，比如我们设置8Mbps,则是1024*1024*8
            mediaFormat.apply {

                setInteger(KEY_COLOR_FORMAT,colorFormat)
                setInteger(KEY_BIT_RATE,width*height)
                //帧率
                setInteger(KEY_FRAME_RATE,framerate)
                //关键帧间隔时间，单位为秒，此处的意思是这个视频每两秒一个关键帧
                setInteger(KEY_I_FRAME_INTERVAL,frame_interval)
            }
                //创建编码器
            var encoder:MediaCodec = MediaCodec.createEncoderByType(MIMETYPE_VIDEO_AVC)
            //最后一个参数需要注意，标明配置的是编码器
            encoder.configure(mediaFormat,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE)
            var mEncoderinputBuffers = encoder.getInputBuffers()

            var inputbufferindex = encoder.dequeueInputBuffer(TIME_OUT_US)
            var  dstBuf:ByteBuffer
            if(inputbufferindex>0){
                dstBuf = mEncoderinputBuffers[inputbufferindex]
                dstBuf.clear()
                dstBuf.limit(bytes.size)
                dstBuf.put(bytes)
                encoder.queueInputBuffer(inputbufferindex,0,bytes.size,0L,0)
            }

//            encoder.createInputSurface()








//            AvcExecuteAsyncTask.execute(BitmapProvider())


        }.start()
//        MediaMuxer("/storage/emulated/0/mvresult.mp4",MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
//        Log.e("ok","meata is ${meta.toString()}")

    }





}