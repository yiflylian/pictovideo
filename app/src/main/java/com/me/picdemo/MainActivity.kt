package com.me.picdemo

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.*
import android.os.Build
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
    lateinit var  mediaCodec:MediaCodec
    lateinit var  mediaMuxer:MediaMuxer
     var  mFrameRate=15
    var mTrackIndex =0
    var mMuxerStarted =false
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

   lateinit var mmr_color:FFmpegMediaMetadataRetriever
   lateinit var mmr_mask:FFmpegMediaMetadataRetriever
   lateinit var bitmap:GPUImage

    var  meta_color_DURATION =0L
    var  meta_color_FRAMERATE =0
    var  meta_color_FRAME_COUNT =0
    var  meta_mask_DURATION =0L
    var  meta_mask_FRAMERATE =0
    var  meta_mask_FRAME_COUNT =0
    var w =720
    var h =1280
    fun getvideoinfo(view: View){



        mmr_color= FFmpegMediaMetadataRetriever()
        mmr_color.setDataSource("/storage/emulated/0/mvColor.mp4")
         meta_color_DURATION =mmr_color.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
          meta_color_FRAMERATE= mmr_color.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_FRAMERATE).toInt()
          meta_color_FRAME_COUNT =meta_color_FRAMERATE*(meta_color_DURATION.toFloat()/1000).toInt()

        mFrameRate = meta_color_FRAMERATE
        Log.e("ok","meta_color_DURATION is ${meta_color_DURATION}")
        Log.e("ok","meta_color_FRAMERATE is ${meta_color_FRAMERATE}")
        Log.e("ok","meta_color_FRAME_COUNT is ${meta_color_FRAME_COUNT}")

         mmr_mask = FFmpegMediaMetadataRetriever()
        mmr_mask.setDataSource("/storage/emulated/0/mvMask.mp4")
         meta_mask_DURATION =mmr_mask.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
          meta_mask_FRAMERATE= mmr_mask.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_FRAMERATE).toInt()
         meta_mask_FRAME_COUNT =meta_mask_FRAMERATE*(meta_mask_DURATION.toFloat()/1000).toInt()

        Log.e("ok","meta_mak_DURATION is ${meta_mask_DURATION}")
        Log.e("ok","meta_mask_FRAMERATE is ${meta_mask_FRAMERATE}")
        Log.e("ok","meta_mak_FRAME_COUNT is ${meta_mask_FRAME_COUNT}")


        bitmap = GPUImage(this)
          var json_data= String(assets.open("data.json").readBytes(), Charset.defaultCharset())
//        Log.e("ok","${json_data}")
        var op =JSONObject(json_data).getInt("op")
       var w= JSONObject(json_data).getInt("w")
       var h= JSONObject(json_data).getInt("h")
        Log.e("ok"," op  is  ${op}")
        var video_format :MediaFormat?=null
        var  mDecodeTrackIndex:Int =0
        var mMimeType:String=""
        var presentationTimeUs:Long =0
         MediaExtractor().apply {

            try {
                setDataSource("/storage/emulated/0/mvColor.mp4")
                val trackCount: Int = getTrackCount()
                Log.e("ok","trackCount is ${trackCount}")
                presentationTimeUs =sampleTime
                Log.e("ok","presentationTimeUs is ${presentationTimeUs}")
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

        var MIME_TYPE = "video/avc" // H.264 Advanced Video Coding

//        video_format = MediaFormat.createVideoFormat(MIME_TYPE,w,h)
        video_format = video_format.apply{
//      var header_sps = byteArrayOf(0, 0, 0, 1, 103, 100, 0, 31, -84, -76, 2, -128, 45, -56)
//    var header_pps = byteArrayOf(0, 0, 0, 1, 104, -18, 60, 97, 15, -1, -16, -121, -1, -8, 67, -1, -4, 33, -1, -2, 16, -1, -1, 8, 127, -1, -64)
//  this?.setByteBuffer("csd-0", ByteBuffer.wrap(header_sps));
//  this?.setByteBuffer("csd-1", ByteBuffer.wrap(header_pps));
            this?.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
//                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)

//            this?.setInteger(MediaFormat.KEY_BIT_RATE, 1048576 * 3)
            this?.setInteger(MediaFormat.KEY_BIT_RATE, w * h)
            this?.setInteger(MediaFormat.KEY_FRAME_RATE, 15)
            this?.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }



        mediaMuxer = MediaMuxer("/storage/emulated/0/result.mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
         mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
//         mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_MPEG4);
            if(mediaCodec==null){
                Log.e("ok","mediaCodec is  null")
                return
            }


        mediaCodec.setCallback(object : MediaCodec.Callback(){
            var  Frameindex =0
            var mTrackIndex =0
            lateinit  var b_result:Bitmap
            lateinit  var  outputbuffer:ByteBuffer
            lateinit var  inputdata:ByteArray
            lateinit var  inputbuffer:ByteBuffer
            var postiontime:Long= 0L
            override fun onOutputBufferAvailable(
                codec: MediaCodec,
                index: Int,
                info: MediaCodec.BufferInfo
            ) {
                Log.e("ok","onOutputBufferAvailable")
                  outputbuffer = codec.getOutputBuffer(index)!!
                outputbuffer?.apply {
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0){
                        // The codec config data was pulled out and fed to the muxer when we got
                        // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                        Log.e("ok", "ignoring BUFFER_FLAG_CODEC_CONFIG")
                        info.size = 0
                    }
                    if(info.size!=0) {
                        position(info.offset)
                        limit(info.offset + info.size)
                        mediaMuxer.writeSampleData(mTrackIndex, outputbuffer, info)
                    }
                    codec.releaseOutputBuffer(index, false)
                    if( info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0){
                        if (codec != null) {
                            codec.stop()
                            codec.release()
                        }
                        if (mediaMuxer != null) {
                            mediaMuxer.stop()
                            mediaMuxer.release()
//                            mediaMuxer = null
                        }

                    }

                }

               }

            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                Log.e("ok","onInputBufferAvailable")
                Log.e("ok","第 ${Frameindex} 帧data")

//                if(inputbuffer==null){
//                    Log.e("ok","onInputBufferAvailable  inputbuffer is null")
//                }
                if(Frameindex<20){
                    b_result= getmergbitmap(Frameindex)
                    if(b_result == null){
                        Log.e("ok","onInputBufferAvailable  b_result is null")
                    }
                    inputdata =  getNV12(getSize(b_result.width),getSize(b_result.height),b_result)
                    if(inputdata == null){
                        Log.e("ok","onInputBufferAvailable  inputdata is null")
                    }else{
                        Log.e("ok","onInputBufferAvailable  inputdata.size is ${inputdata.size}")

                    }
                    inputbuffer = codec.getInputBuffer(index)!!
                    inputbuffer.clear()
                    inputbuffer.put(inputdata)


                    postiontime = (Frameindex/meta_color_FRAMERATE.toFloat()*1000000L).toLong()
                    codec.queueInputBuffer(index,0,inputdata.size,postiontime,0)
                    Frameindex++
                }else{
                    inputbuffer =codec.getInputBuffer(index)!!
                    codec.queueInputBuffer(index,0,0,postiontime,  MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }


              }

            override fun onOutputFormatChanged(p0: MediaCodec, p1: MediaFormat) {
                Log.e("ok","onOutputFormatChanged")
                mTrackIndex = mediaMuxer.addTrack(video_format!!)
                mediaMuxer.start()
               }

            override fun onError(p0: MediaCodec, p1: MediaCodec.CodecException) {
                Log.e("ok","onError")
                Log.e("ok","CodecException is ${p1}")
                }
        })
        mediaCodec.configure(video_format,null,null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        mediaCodec.start()

        return

//        for (Frameindex in 0 until  op){
//
//            b_result= getmergbitmap(Frameindex)
//
//
//
//        }




    }
    fun getmergbitmap(Frameindex:Int):Bitmap{
        return  getmergbitmap(Frameindex,false)
    }
            fun getmergbitmap(Frameindex:Int,saveable:Boolean):Bitmap{
                lottie.frame =Frameindex
                val  b_back =loadBitmapFromView(lottie,w,h)
                var postiontime:Long= (Frameindex/meta_color_FRAMERATE.toFloat()*1000000L).toLong()
                Log.e("ok","postiontime is $postiontime")
                var b_color =  mmr_color.getFrameAtTime(postiontime,FFmpegMediaMetadataRetriever.OPTION_CLOSEST)
                if(b_color == null){
                    Log.d("ok","b_color is  null")
                    b_color =b_back
                }


                var b_mask =  mmr_mask.getFrameAtTime(postiontime,FFmpegMediaMetadataRetriever.OPTION_CLOSEST)
                if(b_mask == null){
                    Log.d("ok","b_mask is  null")
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

                if(saveable){
                    bitmap.saveToPictures(b_color,"reulst/color","color_${Frameindex}.jpg",GPUImage.OnPictureSavedListener {

                        b_color.recycle()
                    })
                    bitmap.saveToPictures(b_mask,"reulst/mask","mask_${Frameindex}.jpg",GPUImage.OnPictureSavedListener {
                        b_mask.recycle()
                    })
                    bitmap.saveToPictures(b_back,"reulst/lottie","lottie_${Frameindex}.jpg",GPUImage.OnPictureSavedListener {
                        b_back.recycle()
                    })
                    bitmap.saveToPictures(b_result,"reulst/result","result_${Frameindex}.jpg",GPUImage.OnPictureSavedListener {
                        b_result.recycle()
                    })
                }

                return b_result
            }

         fun  startVideoCreateThread(){
             Thread{

             }.start()
         }


    private fun drainEncoder(
        endOfStream: Boolean,
        bufferInfo: MediaCodec.BufferInfo
    ) {
        val TIMEOUT_USEC = 10000
        var buffers: Array<ByteBuffer?>? = null
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
            buffers = mediaCodec.getOutputBuffers()
        }
        if (endOfStream) {
            try {
                mediaCodec.signalEndOfInputStream()
            } catch (e: Exception) {
            }
        }
        while (true) {
            val encoderStatus: Int =
                mediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC.toLong())
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) {
                    break // out of while
                } else {
                    Log.e("ok", "no output available, spinning to await EOS")
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (mMuxerStarted) {
                    throw RuntimeException("format changed twice")
                }
                val mediaFormat: MediaFormat = mediaCodec.getOutputFormat()
                mTrackIndex = mediaMuxer.addTrack(mediaFormat)
                mediaMuxer.start()
                mMuxerStarted = true
            } else if (encoderStatus < 0) {
                Log.e("ok",
                    "unexpected result from encoder.dequeueOutputBuffer: $encoderStatus"
                )
            } else {
                var outputBuffer: ByteBuffer? = null
                outputBuffer = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
                    buffers!![encoderStatus]
                } else {
                    mediaCodec.getOutputBuffer(encoderStatus)
                }
                if (outputBuffer == null) {
                    throw RuntimeException(
                        "encoderOutputBuffer "
                                + encoderStatus + " was null"
                    )
                }
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    Log.e("ok", "ignoring BUFFER_FLAG_CODEC_CONFIG")
                    bufferInfo.size = 0
                }
                if (bufferInfo.size != 0) {
                    if (!mMuxerStarted) {
                        throw RuntimeException("muxer hasn't started")
                    }
                    // adjust the ByteBuffer values to match BufferInfo
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                    Log.e("ok", "BufferInfo: " + bufferInfo.offset + ","
                                + bufferInfo.size + ","
                                + bufferInfo.flags + ","
                                + bufferInfo.presentationTimeUs
                    )
                    try {

                        mediaMuxer.writeSampleData(mTrackIndex, outputBuffer, bufferInfo)
                    } catch (e: Exception) {
                        Log.e("ok", "Too many frames")
                    }
                }
                mediaCodec.releaseOutputBuffer(encoderStatus, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    if (!endOfStream) {
                        Log.e("ok", "reached end of stream unexpectedly")
                    } else {
                        Log.e("ok", "end of stream reached")
                        mediaMuxer.stop()
                        mediaMuxer.release()

                    }
                    break // out of while
                }
            }
        }
    }


    /**
     * Generates the presentation time for frame N, in microseconds.
     */
    private fun computePresentationTime(frameIndex: Long): Long {
        return 132 + frameIndex * 1000000 / mFrameRate
    }

    private fun getSize(size: Int): Int {
        return size / 4 * 4
    }

    private fun loadBitmapFromView(v: View,w:Int,h:Int): Bitmap {
       var bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        var c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        /** 如果不设置canvas画布为白色，则生成透明  */
        v.layout(0, 0, w, h)
        v.draw(c)
//        imageView3.setImageBitmap(bmp)
        return bmp
    }

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
fun  main(){
    var meta_color_DURATION =  10000
    var meta_color_FRAME_COUNT = 150
    var frameindex_masktime =0L
   for (i in 0 until 151){
       frameindex_masktime = (i.toFloat() /15*1000000L).toLong()
       println("index / framrate"+frameindex_masktime)
       frameindex_masktime =(meta_color_DURATION.toLong()*1000L/150.toLong()*i)
       println( frameindex_masktime )
       println("======")
   }

}