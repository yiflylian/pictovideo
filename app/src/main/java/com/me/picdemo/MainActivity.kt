package com.me.picdemo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.*
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import com.airbnb.lottie.LottieAnimationView
import com.me.picdemo.databinding.ActivityMainBinding
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.GPUImageAlphaBlendFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageTwoInputFilter
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONObject
import wseemann.media.FFmpegMediaMetadataRetriever
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset


class MainActivity : AppCompatActivity() {
    lateinit var file: File
    lateinit var binding: ActivityMainBinding
    lateinit var viewModel: PicViewModel
    lateinit var mediaCodec: MediaCodec
    lateinit var mediaMuxer: MediaMuxer
    var mFrameRate = 15
    var mTrackIndex = 0
    var mMuxerStarted = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
         binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
         viewModel = ViewModelProvider(this).get(PicViewModel::class.java).apply {
            setdestImageView(binding.imageView3)
        }
        binding.data = viewModel
        binding.setLifecycleOwner(this)
        viewModel.checkPermission(this)
        binding.lottie.imageAssetsFolder = "images"
    }

    lateinit var mmr_color:FFmpegMediaMetadataRetriever
    lateinit var mmr_mask:FFmpegMediaMetadataRetriever
    lateinit var merg_bitmap_color_mask: GPUImage
    lateinit var merg_bitmap_lottie_reult: GPUImage
    lateinit var bitmap: GPUImage

    var meta_color_DURATION = 0L
    var meta_color_FRAMERATE = 0
    var meta_color_FRAME_COUNT = 0
    var meta_mask_DURATION = 0L
    var meta_mask_FRAMERATE = 0
    var meta_mask_FRAME_COUNT = 0
    var w = 540
    var h = 960
    lateinit var lottie_frames: ArrayList<Bitmap>
    lateinit var color_frames: ArrayList<Bitmap>
    lateinit var color_frames_isnullindex: ArrayList<Int>
    lateinit var mask_frames: ArrayList<Bitmap>
    lateinit var mask_frames_isnullindex: ArrayList<Int>
    lateinit var need_jump_frames_indexs: ArrayList<Int>
    lateinit var color_mask_merg_list: ArrayList<Bitmap>
    fun getvideoinfo(view: View) {
        view.isEnabled = false
        view.setBackgroundColor(Color.GRAY)
        val strat_time = System.currentTimeMillis()
        Log.e("ok", "strat_time is ${strat_time}")
//        mmr_color= FFmpegMediaMetadataRetriever()
        bitmap = GPUImage(this)
        var json_data = String(assets.open("data.json").readBytes(), Charset.defaultCharset())
        var op = JSONObject(json_data).getInt("op")
        var w = JSONObject(json_data).getInt("w")
        var h = JSONObject(json_data).getInt("h")


         mmr_color = FFmpegMediaMetadataRetriever().apply {
            setDataSource("/storage/emulated/0/mvColor.mp4")
            meta_color_DURATION =extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
            meta_color_FRAMERATE= extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_FRAMERATE).toInt()
            meta_color_FRAME_COUNT = (meta_color_DURATION /1000 * meta_color_FRAMERATE).toInt()
        }
        mmr_mask = FFmpegMediaMetadataRetriever().apply {
            setDataSource("/storage/emulated/0/mvMask.mp4")
            meta_mask_DURATION =extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
            meta_mask_FRAMERATE= extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_FRAMERATE).toInt()
            meta_mask_FRAME_COUNT = (meta_color_DURATION /1000 * meta_color_FRAMERATE).toInt()
        }
        var video_format: MediaFormat

        video_format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h)
        video_format = video_format.apply {
            //      var header_sps = byteArrayOf(0, 0, 0, 1, 103, 100, 0, 31, -84, -76, 2, -128, 45, -56)
//    var header_pps = byteArrayOf(0, 0, 0, 1, 104, -18, 60, 97, 15, -1, -16, -121, -1, -8, 67, -1, -4, 33, -1, -2, 16, -1, -1, 8, 127, -1, -64)
//  this?.setByteBuffer("csd-0", ByteBuffer.wrap(header_sps));
//  this?.setByteBuffer("csd-1", ByteBuffer.wrap(header_pps));
            this?.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
            )
//                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)

//            this?.setInteger(MediaFormat.KEY_BIT_RATE, 1048576 * 3)
            this?.setInteger(MediaFormat.KEY_BIT_RATE, w * h)
            this?.setInteger(MediaFormat.KEY_FRAME_RATE, 15)
            this?.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }



        mediaMuxer = MediaMuxer(
            "/storage/emulated/0/result.mp4",
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        )
        mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        if (mediaCodec == null) {
                Log.e("ok","mediaCodec is  null")
            return
        }

        lateinit var b_result: Bitmap
        lateinit var outputbuffer: ByteBuffer
        lateinit var inputdata: ByteArray
        mediaCodec.setCallback(object : MediaCodec.Callback() {
            var Frameindex = 0
            var mTrackIndex = 0
            var postiontime: Long = 0L
            override fun onOutputBufferAvailable(
                codec: MediaCodec,
                index: Int,
                info: MediaCodec.BufferInfo
            ) {
//                Log.e("ok","onOutputBufferAvailable")
                outputbuffer = codec.getOutputBuffer(index)!!
                outputbuffer?.apply {
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        // The codec config data was pulled out and fed to the muxer when we got
                        // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
//                        Log.e("ok", "ignoring BUFFER_FLAG_CODEC_CONFIG")
                        info.size = 0
                    }
                    if (info.size != 0) {
                        position(info.offset)
                        limit(info.offset + info.size)
                        mediaMuxer.writeSampleData(mTrackIndex, outputbuffer, info)
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        if (codec != null) {
                            codec.stop()
                            codec.release()
                        }
                        if (mediaMuxer != null) {
                            mediaMuxer.stop()
                            mediaMuxer.release()
                            val end_time = System.currentTimeMillis()
                            Log.e("ok", "end_time is ${end_time}")
                            Log.e("ok", "end_time is ${end_time -strat_time  }")
//                            mediaMuxer = null
                            view.isEnabled = true
                            view.setBackgroundColor(Color.WHITE)
                        }

                    }

                }

            }

            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
//                Log.e("ok","onInputBufferAvailable")
                Log.e("ok", "第 ${Frameindex} 帧data")
                var inputbuffer = codec.getInputBuffer(index)!!
//                if(inputbuffer==null){
//                    Log.e("ok","onInputBufferAvailable  inputbuffer is null")
//                }
//                if (Frameindex < 1) {
                if (Frameindex < op) {
                    var getmergbitmap_time = System.currentTimeMillis()
                    b_result = getmergbitmap(Frameindex)
                    Log.e("ok","getmergbitmap_time  is ${System.currentTimeMillis() - getmergbitmap_time}")
                    if (b_result == null) {
                        Log.e("ok","onInputBufferAvailable  b_result is null")
                    }
//                    b_back?.recycle()
//                    b_color?.recycle()
//                    b_mask?.recycle()
//                    b_result?.recycle()
                    var getNV12_time = System.currentTimeMillis()
                    inputdata = getNV12(getSize(b_result.width), getSize(b_result.height), b_result)
//
                    Log.e("ok","getNV12_time  is ${System.currentTimeMillis() - getNV12_time}")
                    if (inputdata == null) {
                        Log.e("ok","onInputBufferAvailable  inputdata is null")
                    } else {
                        Log.e("ok","onInputBufferAvailable  inputdata.size is ${inputdata.size}")

                    }

                    inputbuffer.clear()
                    inputbuffer.put(inputdata)


                    postiontime = (Frameindex / meta_color_FRAMERATE.toFloat() * 1000000L).toLong()
                    codec.queueInputBuffer(index, 0, inputdata.size, postiontime, 0)
                    Frameindex++
                } else {
//                    inputbuffer =codec.getInputBuffer(index)!!
                    codec.queueInputBuffer(
                        index,
                        0,
                        0,
                        postiontime,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                }


            }

            override fun onOutputFormatChanged(codec: MediaCodec, p1: MediaFormat) {
//                Log.e("ok","onOutputFormatChanged")
                mTrackIndex = mediaMuxer.addTrack(p1)

                mediaMuxer.start()
            }

            override fun onError(p0: MediaCodec, p1: MediaCodec.CodecException) {
//                Log.e("ok","onError")
//                Log.e("ok","CodecException is ${p1}")
            }
        })
        mediaCodec.configure(video_format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        mediaCodec.start()

        return




    }

    var frameIndex =0
    var op =0
    /*****************************/
    var getlottiebitmapable:Boolean = true
    var getcolorbitmapable:Boolean = true
    var getmaskbitmapable:Boolean = true
    var mergpicable:Boolean = true
    var pictobytesable:Boolean = true

    /*****************************/

    var lottie_bitmap:Bitmap?=null
    var color_bitmap:Bitmap?=null
    var mask_bitmap:Bitmap?=null
    var  result:Bitmap?=null
    var minputdata:ByteArray?=null
    /*****************************/
    var  getlottiefinish = false
    var  getcolorfinish = false
    var  getmaskfinish = false

    var mergpicfinish =false
    var  pictobytesfinish = false


    var  mergcolorandmask =false

    /*****************************/
    var start_time:Long =0
    fun  mergvideobysetp(view: View){
        view.isEnabled = false
        view.setBackgroundColor(Color.GRAY)
        start_time = System.currentTimeMillis()
        Log.e("mergvideobysetp","started ")
        bitmap = GPUImage(this)
        var json_data = String(assets.open("data.json").readBytes(), Charset.defaultCharset())
        op = JSONObject(json_data).getInt("op")
         w = JSONObject(json_data).getInt("w")
         h = JSONObject(json_data).getInt("h")
        getlottiebitmap()
        getcolorbitmap()
        getmaskbitmap()
//
        mergpic()
//
//
        pictobytes()
//
        writemp4()


    }

    private fun writemp4() {
        val starttime = System.currentTimeMillis()
        var finished =false
        Thread{
            var video_format: MediaFormat

            video_format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h)
            video_format = video_format.apply {

                this.setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
                )
                this.setInteger(MediaFormat.KEY_BIT_RATE, w * h)
                this.setInteger(MediaFormat.KEY_FRAME_RATE, 15)
                this.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }



            mediaMuxer = MediaMuxer(
                "/storage/emulated/0/result.mp4",
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            if (mediaCodec == null) {
                Log.e("ok","mediaCodec is  null")
                return@Thread
            }

            mediaCodec.configure(video_format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            mediaCodec.start()

            val TIMEOUT_USEC = 10000L
            var inputBufferId =0
            var outputBufferId =0
            var Frameindex =0
            var writeframeindex =0
            var info = MediaCodec.BufferInfo()
            var inputbuffer:ByteBuffer? =null
            var outputBuffer:ByteBuffer? =null
            var bufferFormat:MediaFormat
            while (!finished){

                if(pictobytesfinish ){
                    pictobytesfinish  =false

                    inputBufferId = mediaCodec.dequeueInputBuffer(TIMEOUT_USEC)
                    if (inputBufferId >= 0) {
                         inputbuffer = mediaCodec.getInputBuffer(inputBufferId)
                        if(inputbuffer ==null){
                            Log.e("ok", "inputbuffer is null")
                        }
                        Log.e("ok", "第 ${Frameindex} 帧data")
                        if (Frameindex < op) {
                            var getmergbitmap_time = System.currentTimeMillis()
                            inputbuffer?.clear()
                            inputbuffer?.put(minputdata)
                            postiontime = (Frameindex / meta_color_FRAMERATE.toFloat() * 1000000L).toLong()
                            mediaCodec.queueInputBuffer(inputBufferId, 0, minputdata!!.size, postiontime, 0)
                            Log.e("onInputBufferAvailable"," put $Frameindex")
                                 if(Frameindex ==(op-1)){
                                   pictobytesfinish =true
                                 }
                            Frameindex++


                            minputdata =null


                            pictobytesable =true

                        } else {
//                            Log.e("onInputBufferAvailable"," end")
//                            finished = true
                            mediaCodec.queueInputBuffer(
                                inputBufferId,
                                0,
                                0,
                                postiontime,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                        }


                    }



                }

                outputBufferId = mediaCodec.dequeueOutputBuffer(info,TIMEOUT_USEC);
                if (outputBufferId >= 0) {
                    outputBuffer = mediaCodec.getOutputBuffer(outputBufferId);
                    bufferFormat = mediaCodec.getOutputFormat(outputBufferId); // option A

                    outputBuffer?.apply {
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            // The codec config data was pulled out and fed to the muxer when we got
                            // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
//                        Log.e("ok", "ignoring BUFFER_FLAG_CODEC_CONFIG")
                            info.size = 0
                        }
                        if (info.size != 0) {
                            position(info.offset)
                            limit(info.offset + info.size)
                            mediaMuxer.writeSampleData(mTrackIndex, outputBuffer, info)
                        }
                        mediaCodec.releaseOutputBuffer(outputBufferId, false)
                        Log.e("ok","writeframeindex is  ${writeframeindex} info.flags ${info.flags}")
                        writeframeindex++

                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM )!= 0) {
                            finished = true
                            if (mediaCodec != null) {
                                mediaCodec.stop()
                                mediaCodec.release()
                            }
                            if (mediaMuxer != null) {
                                mediaMuxer.stop()
                                mediaMuxer.release()
                                val end_time = System.currentTimeMillis()
                                Log.e("ok", "end_time is ${end_time}")

                            Log.e("ok", "end_time is ${end_time - start_time  }")
//                            mediaMuxer = null
//                            view.isEnabled = true
//                            view.setBackgroundColor(Color.WHITE)
                            }

                        }

                    }

                    // bufferFormat is identical to outputFormat
                    // outputBuffer is ready to be processed or rendered.

//                        mediaCodec.releaseOutputBuffer(outputBufferId, …);
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // Subsequent data will conform to new format.
                    // Can ignore if using getOutputFormat(outputBufferId)
//                        outputFormat = mediaCodec.getOutputFormat(); // option B
                    mTrackIndex = mediaMuxer.addTrack(mediaCodec.getOutputFormat())
                    Log.e("onOutputFormatChanged","mTrackIndex is  $mTrackIndex")
                    mediaMuxer.start()
                }


            }
            Log.e("ok","end while")
            Log.e("writemp4","耗时 ${System.currentTimeMillis()-starttime}")
            runOnUiThread {
                binding.textView?.apply {
                    isEnabled = true
                    setBackgroundColor(Color.WHITE)
                }

            }

//
        }.start()

    }

    private fun pictobytes() {
        val starttime = System.currentTimeMillis()
        var index=0
        Thread{
            while(index<op){
                if(pictobytesable&&mergpicfinish){
                    pictobytesable=false
                    mergpicfinish=false
                    minputdata=  getNV12(w,h,b_result!!)
                    pictobytesfinish = true
                    if(lottiebitmap_recycle_falg){
                        lottiebitmap_recycle_falg =false
                        lottie_bitmap?.recycle()
                        lottie_bitmap=null
                        getlottiebitmapable =true
                    }
                    b_result?.recycle()
                    b_result = null
                    mergpicable = true


                    Log.e("pictobytes"," 第${index}帧")
                    index++
                }
            }
            Log.e("pictobytes","耗时 ${System.currentTimeMillis()-starttime}")
        }.start()
    }
        var lottiebitmap_recycle_falg =false
    private fun mergpic() {
        val starttime = System.currentTimeMillis()
        var mergpicindex =0
        Thread{
            while (mergpicindex<op){
                if(mergpicable&&getlottiefinish&&getcolorfinish&&getmaskfinish){
                    mergpicable =false

                    getlottiefinish= false
                    getcolorfinish= false
                    getmaskfinish= false
                    if(lottie_bitmap==null){
                        Log.e("mergpic"," 第${mergpicindex}帧 lottie_bitmap is null")
                    }

                    if(color_bitmap==null){
                        getcolorbitmapable =true

                        Log.e("mergpic"," 第${mergpicindex}帧 color_bitmap is null")
                    }
                    if(mask_bitmap==null){
                        getmaskbitmapable = true
                        Log.e("mergpic"," 第${mergpicindex}帧 mask_bitmap is null")
                    }

                    if(color_bitmap!=null&&mask_bitmap!=null){
                        bitmap.setImage(color_bitmap)
                        bitmap.setFilter(GPUImageTwoInputFilter(PicViewModel.TEST_ALPHA_FRAGMENT_SHADER).apply {
                            bitmap = mask_bitmap
                        })
                        result = bitmap.bitmapWithFilterApplied
                        if(result ==null){
                            Log.e("mergpic"," 第${mergpicindex}帧 result is null")
                        }
//

                        color_bitmap?.recycle()
                        color_bitmap= null

                        mask_bitmap?.recycle()
                        mask_bitmap= null

                        getcolorbitmapable =true
                        getmaskbitmapable = true
//
//
//                        b_result?.recycle()
//                        b_result =null
                        bitmap.setImage(lottie_bitmap)
                        bitmap.setFilter(GPUImageAlphaBlendFilter().apply {
                            bitmap = result
                        })
                        b_result = bitmap.bitmapWithFilterApplied
                        if(b_result==null){
                            Log.e("mergpic"," 第${mergpicindex}帧 b_result is null")
                        }else{

                            lottie_bitmap?.recycle()
                            lottie_bitmap= null
                            getlottiebitmapable = true
                            result?.recycle()
                            result =null
                        }
                    }else{
                        b_result =lottie_bitmap
                        lottiebitmap_recycle_falg = true
                    }


                    Log.e("mergpic"," 第${mergpicindex}帧")
                    mergpicfinish = true //结束flag



                    mergpicindex++


                }

            }
            Log.e("mergpic","耗时 ${System.currentTimeMillis()-starttime}")
        }.start()

    }

    private fun getmaskbitmap() {
       val starttime = System.currentTimeMillis()
        mmr_mask = FFmpegMediaMetadataRetriever().apply {
            setDataSource("/storage/emulated/0/mvMask.mp4")
            meta_mask_DURATION =extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
            meta_mask_FRAMERATE= extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_FRAMERATE).toInt()
            meta_mask_FRAME_COUNT = (meta_color_DURATION /1000 * meta_color_FRAMERATE).toInt()
        }
//        var mmr_mask  = MediaMetadataRetriever().apply {
//            setDataSource("/storage/emulated/0/mvMask.mp4")
//            meta_mask_DURATION = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
//            meta_mask_FRAME_COUNT= extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT).toInt()
//            meta_mask_FRAMERATE =   meta_mask_FRAME_COUNT/(meta_mask_DURATION/1000).toInt()
//            Log.e("getmaskbitmap","meta_mask_FRAMERATE is $meta_mask_FRAMERATE")
//        }
        var  maskframeIndex =0
        Thread{
            var oneframestarttime =0L
            while(maskframeIndex<op){
                if(getmaskbitmapable){
                    getmaskbitmapable= false
                    oneframestarttime = System.currentTimeMillis()
                    mask_bitmap =mmr_mask.getFrameAtTime(((maskframeIndex.toFloat()/meta_mask_FRAMERATE)*1000000L).toLong(),FFmpegMediaMetadataRetriever.OPTION_CLOSEST)
//                    mask_bitmap =mmr_mask.getFrameAtTime(((maskframeIndex.toFloat()/meta_mask_FRAMERATE)*1000000L).toLong(),MediaMetadataRetriever.OPTION_CLOSEST)

                    Log.e("getmaskbitmap"," 第${maskframeIndex}帧 耗时${System.currentTimeMillis()-oneframestarttime}")
                    getmaskfinish =true
//                    Thread.sleep(1000)
                    maskframeIndex++
                }
            }
            mmr_mask.release()
            Log.e("getmaskbitmap","耗时 ${System.currentTimeMillis()-starttime}")
        }.start()
    }

    private fun getcolorbitmap() {
        val starttime = System.currentTimeMillis()
        mmr_color = FFmpegMediaMetadataRetriever().apply {
            setDataSource("/storage/emulated/0/mvColor.mp4")
            meta_color_DURATION =extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
            meta_color_FRAMERATE= extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_FRAMERATE).toInt()
            meta_color_FRAME_COUNT = (meta_color_DURATION /1000 * meta_color_FRAMERATE).toInt()
        }
//        var  mmr_color =MediaMetadataRetriever().apply {
//            setDataSource("/storage/emulated/0/mvColor.mp4")
//            meta_color_DURATION =extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
////
//            meta_color_FRAME_COUNT = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT).toInt()
//            meta_color_FRAMERATE=   meta_color_FRAME_COUNT/(meta_color_DURATION/1000).toInt()
//            Log.e("getcolorbitmap","meta_color_FRAMERATE is $meta_color_FRAMERATE")
//            }
        var  colorframeIndex =0
        var postiontime =0L
        Thread{
            var oneframestarttime =0L
            while(colorframeIndex<op){
                if(getcolorbitmapable){
                    getcolorbitmapable= false
                    oneframestarttime =  System.currentTimeMillis()
//                    postiontime = (colorframeIndex / meta_color_FRAMERATE.toFloat() * 1000000L).toLong()
//                    Log.e("getcolorbitmap "," 第${colorframeIndex}帧  postiontime is ${postiontime}")
//                    color_bitmap =mmr_color.getFrameAtTime(postiontime,FFmpegMediaMetadataRetriever.OPTION_CLOSEST)
                    color_bitmap =mmr_color.getFrameAtTime((colorframeIndex / meta_color_FRAMERATE.toFloat() * 1000000L).toLong(),FFmpegMediaMetadataRetriever.OPTION_CLOSEST)
//                    color_bitmap =mmr_color.getFrameAtTime(postiontime,MediaMetadataRetriever.OPTION_CLOSEST)
                    Log.e("getcolorbitmap"," 第${colorframeIndex}帧 耗时${System.currentTimeMillis()-oneframestarttime}")
//                    Thread.sleep(1000)
                    if(color_bitmap==null){
                        Log.e("getcolorbitmap"," 第${colorframeIndex}帧 is  null")
                    }else{
//                        Log.e("getcolorbitmap"," 第${colorframeIndex}帧 is  save")
//                        bitmap.saveToPictures(
//                            color_bitmap,
//                            "reulst/color",
//                            "color_${colorframeIndex}.jpg",
//                            GPUImage.OnPictureSavedListener {
//                                color_bitmap?.recycle()
//                                color_bitmap =null
//                                getcolorbitmapable = true
//                            })
                    }


                    getcolorfinish =true
                    colorframeIndex++
                }
            }
            mmr_color.release()
            Log.e("getcolorbitmap","耗时 ${System.currentTimeMillis()-starttime}")
        }.start()
    }

    private fun getlottiebitmap() {
        val starttime = System.currentTimeMillis()
        var lottieindex =0
        Thread{
            while(lottieindex<op){
                if(getlottiebitmapable){
                    getlottiebitmapable= false
                    binding.lottie.frame  = lottieindex
                    lottie_bitmap =loadBitmapFromView(binding.lottie,getSize(w),getSize(h))
                    Log.e("getlottiebitmap"," 第${lottieindex}帧")

//                    Thread.sleep(1000)
                    getlottiefinish =true
                    lottieindex++

                }
            }
            Log.e("getlottiebitmap","耗时 ${System.currentTimeMillis()-starttime}")

        }.start()
    }

    fun mergvideobyThread(view: View){
        view.isEnabled = false
        view.setBackgroundColor(Color.GRAY)
        val strat_time = System.currentTimeMillis()
        Log.e("ok", "strat_time is ${strat_time}")

        merg_bitmap_color_mask = GPUImage(this)
        merg_bitmap_lottie_reult = GPUImage(this)
        var json_data = String(assets.open("data.json").readBytes(), Charset.defaultCharset())
        var op = JSONObject(json_data).getInt("op")
        var w = JSONObject(json_data).getInt("w")
        var h = JSONObject(json_data).getInt("h")
        lottie_frames=ArrayList<Bitmap>()
        color_frames=ArrayList<Bitmap>()
        color_frames_isnullindex = ArrayList<Int>()
        mask_frames=ArrayList<Bitmap>()
        mask_frames_isnullindex = ArrayList<Int>()
        need_jump_frames_indexs = ArrayList<Int>()
        mask_frames=ArrayList<Bitmap>()
        color_mask_merg_list=ArrayList<Bitmap>()
        StartGetLottieFrameTask(binding.lottie,op,lottie_frames)
        StartGetVideoFrameTask("/storage/emulated/0/mvColor.mp4",op,color_frames,color_frames_isnullindex)
        StartGetVideoFrameTask("/storage/emulated/0/mvMask.mp4",op,mask_frames, mask_frames_isnullindex)
        StartMergVideoTask(color_frames,mask_frames,color_mask_merg_list,need_jump_frames_indexs,op,merg_bitmap_color_mask)
        return
        var video_format= MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            //      var header_sps = byteArrayOf(0, 0, 0, 1, 103, 100, 0, 31, -84, -76, 2, -128, 45, -56)
//    var header_pps = byteArrayOf(0, 0, 0, 1, 104, -18, 60, 97, 15, -1, -16, -121, -1, -8, 67, -1, -4, 33, -1, -2, 16, -1, -1, 8, 127, -1, -64)
//  this?.setByteBuffer("csd-0", ByteBuffer.wrap(header_sps));
//  this?.setByteBuffer("csd-1", ByteBuffer.wrap(header_pps));
            this?.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
            )
//                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
//            this?.setInteger(MediaFormat.KEY_BIT_RATE, 1048576 * 3)
            this?.setInteger(MediaFormat.KEY_BIT_RATE, w * h)
            this?.setInteger(MediaFormat.KEY_FRAME_RATE, 15)
            this?.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }



        mediaMuxer = MediaMuxer(
            "/storage/emulated/0/result.mp4",
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        )
        mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        if (mediaCodec == null) {
            Log.e("ok","mediaCodec is  null")
            return
        }

        lateinit var b_result: Bitmap
        lateinit var outputbuffer: ByteBuffer
        lateinit var inputdata: ByteArray
        mediaCodec.setCallback(object : MediaCodec.Callback() {
            var Frameindex = 0
            var mTrackIndex = 0
            var postiontime: Long = 0L
            override fun onOutputBufferAvailable(
                codec: MediaCodec,
                index: Int,
                info: MediaCodec.BufferInfo
            ) {
//                Log.e("ok","onOutputBufferAvailable")
                outputbuffer = codec.getOutputBuffer(index)!!
                outputbuffer?.apply {
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        // The codec config data was pulled out and fed to the muxer when we got
                        // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
//                        Log.e("ok", "ignoring BUFFER_FLAG_CODEC_CONFIG")
                        info.size = 0
                    }
                    if (info.size != 0) {
                        position(info.offset)
                        limit(info.offset + info.size)
                        mediaMuxer.writeSampleData(mTrackIndex, outputbuffer, info)
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        if (codec != null) {
                            codec.stop()
                            codec.release()
                        }
                        if (mediaMuxer != null) {
                            mediaMuxer.stop()
                            mediaMuxer.release()
                            val end_time = System.currentTimeMillis()
                            Log.e("ok", "end_time is ${end_time}")
                            Log.e("ok", "end_time is ${end_time -strat_time  }")
//                            mediaMuxer = null
                            view.isEnabled = true
                            view.setBackgroundColor(Color.WHITE)
                        }

                    }

                }

            }

            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
//                Log.e("ok","onInputBufferAvailable")
                Log.e("ok", "第 ${Frameindex} 帧data")
                var inputbuffer = codec.getInputBuffer(index)!!
//                if(inputbuffer==null){
//                    Log.e("ok","onInputBufferAvailable  inputbuffer is null")
//                }
//                if (Frameindex < 1) {
                if (Frameindex < op) {
                    var getmergbitmap_time = System.currentTimeMillis()
                    b_result = getmergbitmap(Frameindex)
                    Log.e("ok","getmergbitmap_time  is ${System.currentTimeMillis() - getmergbitmap_time}")
                    if (b_result == null) {
                        Log.e("ok","onInputBufferAvailable  b_result is null")
                    }

                    var getNV12_time = System.currentTimeMillis()
                    inputdata = getNV12(getSize(b_result.width), getSize(b_result.height), b_result)

                    Log.e("ok","getNV12_time  is ${System.currentTimeMillis() - getNV12_time}")
                    if (inputdata == null) {
                        Log.e("ok","onInputBufferAvailable  inputdata is null")
                    } else {
                        Log.e("ok","onInputBufferAvailable  inputdata.size is ${inputdata.size}")

                    }

                    inputbuffer.clear()
                    inputbuffer.put(inputdata)


                    postiontime = (Frameindex / meta_color_FRAMERATE.toFloat() * 1000000L).toLong()
                    codec.queueInputBuffer(index, 0, inputdata.size, postiontime, 0)
                    Frameindex++
                } else {
//                    inputbuffer =codec.getInputBuffer(index)!!
                    codec.queueInputBuffer(
                        index,
                        0,
                        0,
                        postiontime,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                }


            }

            override fun onOutputFormatChanged(codec: MediaCodec, p1: MediaFormat) {
//                Log.e("ok","onOutputFormatChanged")
                mTrackIndex = mediaMuxer.addTrack(p1)

                mediaMuxer.start()
            }

            override fun onError(p0: MediaCodec, p1: MediaCodec.CodecException) {
//                Log.e("ok","onError")
//                Log.e("ok","CodecException is ${p1}")
            }
        })
        mediaCodec.configure(video_format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        mediaCodec.start()

        return
    }

    private fun StartMergVideoTask(
            colorFrames: ArrayList<Bitmap>,
            maskFrames: ArrayList<Bitmap>,
            colorMaskMergList: ArrayList<Bitmap>,
            need_jump_frames_indexs: ArrayList<Int>,
            op: Int,
            mergBitmapColorMask: GPUImage
    ) {
        var b_result:Bitmap
        var b_color:Bitmap
        var b_mask:Bitmap
        var index:Int=0
        Thread{
           while (colorMaskMergList.size<op){
               index =colorMaskMergList.size
               if(index<lottie_frames.size){
                   if (color_frames_isnullindex.contains(index)&&mask_frames_isnullindex.contains(index)){ //color 、 mask  这帧都是null  这帧不做任何操作 添加大需要跳过的帧的index集合中
                       need_jump_frames_indexs.add(index)
                       colorMaskMergList.add(lottie_frames[index])

                   }else{
                       if(color_frames_isnullindex.contains(index)){
                           colorFrames.add(index,lottie_frames[index])
                           color_frames_isnullindex.remove(index)
                       }
                       if(mask_frames_isnullindex.contains(index)){
                           maskFrames.add(index,lottie_frames[index])
                           mask_frames_isnullindex.remove(index)
                       }
                       if(index<colorFrames.size && index < maskFrames.size){
                           b_color = colorFrames[index]
                           b_mask = maskFrames[index]
                           mergBitmapColorMask.setImage(b_color)
                           mergBitmapColorMask.setFilter( GPUImageTwoInputFilter(PicViewModel.TEST_ALPHA_FRAGMENT_SHADER).apply {
                               bitmap =b_mask
                           })
                           b_result = mergBitmapColorMask.bitmapWithFilterApplied
                           if(b_result ==null){
                               Log.e("merg","b_result is  null")
                           }else{
                               colorMaskMergList.add(b_result)
                           }

                       }

                   }

               }


           }


        }.start()
    }

    private fun StartGetLottieFrameTask(lottie: LottieAnimationView, op: Int, lottieFrames: ArrayList<Bitmap>) {
        val start_time = System.currentTimeMillis()
        Log.e("lottie","start StartGetLottieFrameTask")
        Thread{
            var b_lottie:Bitmap
            for (FrameIndex in 0 until op){
                Log.e("lottie","lottieFrames 第${FrameIndex}帧")
                lottie.frame =FrameIndex
                b_lottie=loadBitmapFromView(lottie,w,h)
                lottieFrames.add(b_lottie)
            }
            Log.e("lottie","end StartGetLottieFrameTask ${System.currentTimeMillis()-start_time}")
            Log.e("lottie","lottieFrames.size is${lottieFrames.size}")
        }.start()

    }

    //    <editor-fold desc ="获取color frames">
    fun StartGetVideoFrameTask (videopath:String,FrameCount: Int,destList:ArrayList<Bitmap>,FrameIsNullIndexList:ArrayList<Int>){
       val start_time =  System.currentTimeMillis()
        val videotag =if(videopath.contains("mvColor.mp4")) "color" else "mask "

       Log.e(videotag,"StartGetVideoFrameTask  start_time is  ${start_time}")
       destList.clear()
       FrameIsNullIndexList.clear()
       var postiontime:Long
       var  colorframeitem :Bitmap?=null
        var mmr_color = FFmpegMediaMetadataRetriever().apply {
            setDataSource(videopath)
            meta_color_DURATION =extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
            meta_color_FRAMERATE= extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_FRAMERATE).toInt()
            meta_color_FRAME_COUNT = (meta_color_DURATION /1000 * meta_color_FRAMERATE).toInt()
        }
        Thread{

            Log.e(videotag, "meta_color_DURATION is ${meta_color_DURATION}")
            Log.e(videotag, "meta_color_FRAMERATE is ${meta_color_FRAMERATE}")
            Log.e(videotag, "meta_color_FRAME_COUNT is ${meta_color_FRAME_COUNT}")
            for(Frameindex in 0 until FrameCount){
                Log.e(videotag, "第 ${Frameindex}/${FrameCount}帧")
                postiontime = (Frameindex.toFloat() / meta_color_FRAMERATE * 1000000L).toLong()
//                postiontime = 132 + Frameindex.toLong() * 1000000 / meta_color_FRAMERATE
                Log.e(videotag ,"postiontime is  ${postiontime}")
                colorframeitem =mmr_color.getFrameAtTime(postiontime, MediaMetadataRetriever.OPTION_CLOSEST)
                if(colorframeitem==null){
                    Log.e(videotag,"colorframeitem is  null")
                    FrameIsNullIndexList.add(Frameindex)
                }else{
                    destList.add(colorframeitem!!)
//                    colorframeitem!!.recycle()
                }

            }
            mmr_color.release()
            Log.e(videotag,"StartGetVideoFrameTask  time is  ${System.currentTimeMillis()-start_time}")
            Log.e(videotag,"destList.size  time is  ${destList.size}")
            if(destList[0]==null){
                Log.e(videotag,"destList[0] is  null")


            }
        }.start()
    }
    //</editor-fold>
//    <editor-fold desc ="获取3合1图片结果">
    fun getmergbitmap(Frameindex: Int): Bitmap {
        return getmergbitmap(Frameindex, false)
    }
    var  b_color:Bitmap?=null
    var  b_mask:Bitmap?=null
    var  b_back:Bitmap?=null

    var  b_result:Bitmap?=null
    var postiontime: Long = 0L

    fun getmergbitmap(Frameindex: Int, saveable: Boolean): Bitmap {
        Log.e("ok","Frameindex is $Frameindex")
        binding.lottie.frame = Frameindex
         b_back = loadBitmapFromView(binding.lottie, w, h)
        postiontime = (Frameindex / meta_color_FRAMERATE.toFloat() * 1000000L).toLong()
                Log.e("ok","postiontime is $postiontime")
                 b_color =  mmr_color.getFrameAtTime(postiontime,FFmpegMediaMetadataRetriever.OPTION_CLOSEST)
//        if(colorframes_isnullindex.contains(Frameindex)){
//            colorframes.add(Frameindex,b_back)
//            colorframes_isnullindex.remove(Frameindex)
//        }
//        if(colorframes_isnullindex.contains(Frameindex)){
//            colorframes.add(Frameindex,b_back)
//            colorframes_isnullindex.remove(Frameindex)
//        }
//        var b_color = colorframes[Frameindex]
        if (b_color == null) {
//                    Log.d("ok","b_color is  null")
            b_color = b_back
        }



         b_mask =
            mmr_mask.getFrameAtTime(postiontime, MediaMetadataRetriever.OPTION_CLOSEST)
        if (b_mask == null) {
                    Log.d("ok","b_mask is  null")
            b_mask = b_back
        }


        bitmap.setImage(b_color)

        var  filter = GPUImageTwoInputFilter(PicViewModel.TEST_ALPHA_FRAGMENT_SHADER)
        filter.bitmap = b_mask
        bitmap.setFilter(filter)
         result = bitmap.bitmapWithFilterApplied



        bitmap.setImage(b_back)
        var b_filter = GPUImageAlphaBlendFilter()
        b_filter.bitmap = result
        bitmap.setFilter(b_filter)
         b_result = bitmap.bitmapWithFilterApplied


        if (saveable) {
            bitmap.saveToPictures(
                b_color,
                "reulst/color",
                "color_${Frameindex}.jpg",
                GPUImage.OnPictureSavedListener {

                    b_color?.recycle()
                })
            bitmap.saveToPictures(
                b_mask,
                "reulst/mask",
                "mask_${Frameindex}.jpg",
                GPUImage.OnPictureSavedListener {
                    b_mask?.recycle()
                })
            bitmap.saveToPictures(
                b_back,
                "reulst/lottie",
                "lottie_${Frameindex}.jpg",
                GPUImage.OnPictureSavedListener {
                    b_back?.recycle()
                })
            bitmap.saveToPictures(
                b_result,
                "reulst/result",
                "result_${Frameindex}.jpg",
                GPUImage.OnPictureSavedListener {
                    b_result?.recycle()
                })
        }
//        b_color.recycle()
//        b_mask.recycle()
//        b_back.recycle()
//        result.recycle()
        return b_result!!
    }


    //</editor-fold>


    /**
     * Generates the presentation time for frame N, in microseconds.
     */
    private fun computePresentationTime(frameIndex: Long): Long {
        return 132 + frameIndex * 1000000 / mFrameRate
    }

    private fun getSize(size: Int): Int {
        return size / 4 * 4
    }
//    <editor-fold desc ="获取view图片">
    private fun loadBitmapFromView(v: View, w: Int, h: Int): Bitmap {
        var bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        var c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        /** 如果不设置canvas画布为白色，则生成透明  */
        v.layout(0, 0, w, h)
        v.draw(c)
//        imageView3.setImageBitmap(bmp)
        return bmp
    }
    //</editor-fold>
    private fun getNV12(
        inputWidth: Int,
        inputHeight: Int,
        scaled: Bitmap
    ): ByteArray { // Reference (Variation) : https://gist.github.com/wobbals/5725412
        val argb = IntArray(inputWidth * inputHeight)
        //Log.i(TAG, "scaled : " + scaled);
        scaled.getPixels(argb, 0, inputWidth, 0, 0, inputWidth, inputHeight)
        val yuv = ByteArray(inputWidth * inputHeight * 3 / 2)
//        when (colorFormat) {
//            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar -> encodeYUV420SP(
//                yuv,
//                argb,
//                inputWidth,
//                inputHeight
//            )
//            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar -> encodeYUV420P(
//                yuv,
//                argb,
//                inputWidth,
//                inputHeight
//            )
//            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar -> encodeYUV420PSP(
//                yuv,
//                argb,
//                inputWidth,
//                inputHeight
//            )
//            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar -> encodeYUV420PP(
//                yuv,
//                argb,
//                inputWidth,
//                inputHeight
//            )
//        }
        //        scaled.recycle();
        encodeYUV420SP(
            yuv,
            argb,
            inputWidth,
            inputHeight
        )
        return yuv
    }

    /**
     * Returns the first codec capable of encoding the specified MIME type, or null if no
     * match was found.
     */
    fun selectCodec(mimeType: String): MediaCodecInfo? {
        val numCodecs = MediaCodecList.getCodecCount()
        for (i in 0 until numCodecs) {
            val codecInfo = MediaCodecList.getCodecInfoAt(i)
            if (!codecInfo.isEncoder) {
                continue
            }
            val types = codecInfo.supportedTypes
            for (j in types.indices) {
                if (types[j].equals(mimeType, ignoreCase = true)) {
                    return codecInfo
                }
            }
        }
        return null
    }

    private fun encodeYUV420SP(
        yuv420sp: ByteArray,
        argb: IntArray,
        width: Int,
        height: Int
    ) {
        val frameSize = width * height
        var yIndex = 0
        var uvIndex = frameSize
        var a: Int
        var R: Int
        var G: Int
        var B: Int
        var Y: Int
        var U: Int
        var V: Int
        var index = 0
        for (j in 0 until height) {
            for (i in 0 until width) {
                a = argb[index] and -0x1000000 shr 24 // a is not used obviously
                R = argb[index] and 0xff0000 shr 16
                G = argb[index] and 0xff00 shr 8
                B = argb[index] and 0xff shr 0
                //                R = (argb[index] & 0xff000000) >>> 24;
//                G = (argb[index] & 0xff0000) >> 16;
//                B = (argb[index] & 0xff00) >> 8;
// well known RGB to YUV algorithm
                Y = (66 * R + 129 * G + 25 * B + 128 shr 8) + 16
                V = (-38 * R - 74 * G + 112 * B + 128 shr 8) + 128 // Previously U
                U = (112 * R - 94 * G - 18 * B + 128 shr 8) + 128 // Previously V
                yuv420sp[yIndex++] =
                    (if (Y < 0) 0 else if (Y > 255) 255 else Y).toByte()
                if (j % 2 == 0 && index % 2 == 0) {
                    yuv420sp[uvIndex++] =
                        (if (V < 0) 0 else if (V > 255) 255 else V).toByte()
                    yuv420sp[uvIndex++] =
                        (if (U < 0) 0 else if (U > 255) 255 else U).toByte()
                }
                index++
            }
        }
    }

    private fun encodeYUV420P(
        yuv420sp: ByteArray,
        argb: IntArray,
        width: Int,
        height: Int
    ) {
        val frameSize = width * height
        var yIndex = 0
        var uIndex = frameSize
        var vIndex = frameSize + width * height / 4
        var a: Int
        var R: Int
        var G: Int
        var B: Int
        var Y: Int
        var U: Int
        var V: Int
        var index = 0
        for (j in 0 until height) {
            for (i in 0 until width) {
                a = argb[index] and -0x1000000 shr 24 // a is not used obviously
                R = argb[index] and 0xff0000 shr 16
                G = argb[index] and 0xff00 shr 8
                B = argb[index] and 0xff shr 0
                //                R = (argb[index] & 0xff000000) >>> 24;
//                G = (argb[index] & 0xff0000) >> 16;
//                B = (argb[index] & 0xff00) >> 8;
// well known RGB to YUV algorithm
                Y = (66 * R + 129 * G + 25 * B + 128 shr 8) + 16
                V = (-38 * R - 74 * G + 112 * B + 128 shr 8) + 128 // Previously U
                U = (112 * R - 94 * G - 18 * B + 128 shr 8) + 128 // Previously V
                yuv420sp[yIndex++] =
                    (if (Y < 0) 0 else if (Y > 255) 255 else Y).toByte()
                if (j % 2 == 0 && index % 2 == 0) {
                    yuv420sp[vIndex++] =
                        (if (U < 0) 0 else if (U > 255) 255 else U).toByte()
                    yuv420sp[uIndex++] =
                        (if (V < 0) 0 else if (V > 255) 255 else V).toByte()
                }
                index++
            }
        }
    }

    private fun encodeYUV420PSP(
        yuv420sp: ByteArray,
        argb: IntArray,
        width: Int,
        height: Int
    ) {
        val frameSize = width * height
        var yIndex = 0
        //        int uvIndex = frameSize;
        var a: Int
        var R: Int
        var G: Int
        var B: Int
        var Y: Int
        var U: Int
        var V: Int
        var index = 0
        for (j in 0 until height) {
            for (i in 0 until width) {
                a = argb[index] and -0x1000000 shr 24 // a is not used obviously
                R = argb[index] and 0xff0000 shr 16
                G = argb[index] and 0xff00 shr 8
                B = argb[index] and 0xff shr 0
                //                R = (argb[index] & 0xff000000) >>> 24;
//                G = (argb[index] & 0xff0000) >> 16;
//                B = (argb[index] & 0xff00) >> 8;
// well known RGB to YUV algorithm
                Y = (66 * R + 129 * G + 25 * B + 128 shr 8) + 16
                V = (-38 * R - 74 * G + 112 * B + 128 shr 8) + 128 // Previously U
                U = (112 * R - 94 * G - 18 * B + 128 shr 8) + 128 // Previously V
                yuv420sp[yIndex++] =
                    (if (Y < 0) 0 else if (Y > 255) 255 else Y).toByte()
                if (j % 2 == 0 && index % 2 == 0) {
                    yuv420sp[yIndex + 1] =
                        (if (V < 0) 0 else if (V > 255) 255 else V).toByte()
                    yuv420sp[yIndex + 3] =
                        (if (U < 0) 0 else if (U > 255) 255 else U).toByte()
                }
                if (index % 2 == 0) {
                    yIndex++
                }
                index++
            }
        }
    }

    private fun encodeYUV420PP(
        yuv420sp: ByteArray,
        argb: IntArray,
        width: Int,
        height: Int
    ) {
        var yIndex = 0
        var vIndex = yuv420sp.size / 2
        var a: Int
        var R: Int
        var G: Int
        var B: Int
        var Y: Int
        var U: Int
        var V: Int
        var index = 0
        for (j in 0 until height) {
            for (i in 0 until width) {
                a = argb[index] and -0x1000000 shr 24 // a is not used obviously
                R = argb[index] and 0xff0000 shr 16
                G = argb[index] and 0xff00 shr 8
                B = argb[index] and 0xff shr 0
                //                R = (argb[index] & 0xff000000) >>> 24;
//                G = (argb[index] & 0xff0000) >> 16;
//                B = (argb[index] & 0xff00) >> 8;
// well known RGB to YUV algorithm
                Y = (66 * R + 129 * G + 25 * B + 128 shr 8) + 16
                V = (-38 * R - 74 * G + 112 * B + 128 shr 8) + 128 // Previously U
                U = (112 * R - 94 * G - 18 * B + 128 shr 8) + 128 // Previously V
                if (j % 2 == 0 && index % 2 == 0) { // 0
                    yuv420sp[yIndex++] =
                        (if (Y < 0) 0 else if (Y > 255) 255 else Y).toByte()
                    yuv420sp[yIndex + 1] =
                        (if (V < 0) 0 else if (V > 255) 255 else V).toByte()
                    yuv420sp[vIndex + 1] =
                        (if (U < 0) 0 else if (U > 255) 255 else U).toByte()
                    yIndex++
                } else if (j % 2 == 0 && index % 2 == 1) { //1
                    yuv420sp[yIndex++] =
                        (if (Y < 0) 0 else if (Y > 255) 255 else Y).toByte()
                } else if (j % 2 == 1 && index % 2 == 0) { //2
                    yuv420sp[vIndex++] =
                        (if (Y < 0) 0 else if (Y > 255) 255 else Y).toByte()
                    vIndex++
                } else if (j % 2 == 1 && index % 2 == 1) { //3
                    yuv420sp[vIndex++] =
                        (if (Y < 0) 0 else if (Y > 255) 255 else Y).toByte()
                }
                index++
            }
        }
    }

    fun getMediaCodecList(): IntArray? { //获取解码器列表
        val numCodecs = MediaCodecList.getCodecCount()
        var codecInfo: MediaCodecInfo? = null
        var i = 0
        while (i < numCodecs && codecInfo == null) {
            val info = MediaCodecList.getCodecInfoAt(i)
            if (!info.isEncoder) {
                i++
                continue
            }
            val types = info.supportedTypes
            var found = false
            //轮训所要的解码器
            var j = 0
            while (j < types.size && !found) {
                if (types[j] == "video/avc") {
                    found = true
                }
                j++
            }
            if (!found) {
                i++
                continue
            }
            codecInfo = info
            i++
        }
        Log.e(
            "ok",
            "found" + codecInfo!!.name + "supporting" + " video/avc"
        )
        val capabilities = codecInfo.getCapabilitiesForType("video/avc")
        return capabilities.colorFormats
    }
}

fun main() {
    var meta_color_DURATION = 10000
    var meta_color_FRAME_COUNT = 150
    var frameindex_masktime = 0L

    println("flase ${false &&false}")
//    for(i in 0 until  150){
//        println(i.toFloat()/15*1000000)
//        println(132 + i.toLong() * 1000000 / 15)
//
//    }

}