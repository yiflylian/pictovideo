package com.me.picdemo;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author simpler
 * @create 2020年05月22日  13:47
 */
class Mp4Converter {
    private int mWidth = 720;
    private int mHeight = 1280;
    private static final boolean VERBOSE = true;
    private static final String TAG = "Mp4Converter";
    private static final String MIME_TYPE = "video/avc"; // H.264 Advanced Video Coding
    private MediaExtractor mMediaExtractor;
    private MediaFormat mVideoFormat;
    private int mDecodeTrackIndex = -1;
    private int mEncodeTrackIndex = -1;
    private String mMimeType;
    private int mRotation;
    private Surface mOutputSurface;
    private MediaMuxer mMuxer;
    private boolean mMuxerStarted;
    private static final long TIME_OUT_US = 10000;

    public void convert(String source, String target) {
        extractor(source);
        MediaCodec encoder = null;
        MediaCodec decoder = null;
        InputSurface inputSurface = null;
        CodecOutputSurface outputSurface = null;
        try {
            File outputFile = new File(target);
            if(outputFile.exists()){
                outputFile.delete();
            }
            mMuxer = new MediaMuxer(target, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            mEncodeTrackIndex = -1;
            mMuxerStarted = false;

            MediaCodecInfo codecInfo = selectCodec(MIME_TYPE);
            if (codecInfo == null) { // Don't fail CTS if they don't have an AVC codec (not here, anyway).
                Log.e(TAG, "Unable to find an appropriate codec for " + MIME_TYPE);
                return;
            }
            if (VERBOSE) Log.d(TAG, "found codec: " + codecInfo.getName());


            // Create an encoder format that matches the input format.  (Might be able to just
            // re-use the format used to generate the video, since we want it to be the same.)
            MediaFormat outputFormat = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
            outputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, 1048576 * 3);
            outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
            outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);



            encoder = MediaCodec.createEncoderByType(MIME_TYPE);
            encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = new InputSurface(encoder.createInputSurface());
            encoder.start(); // OutputSurface uses the EGL context created by InputSurface.


            decoder = MediaCodec.createDecoderByType(MIME_TYPE);
            outputSurface = new CodecOutputSurface();
            decoder.configure(mVideoFormat, outputSurface.getSurface(), null, 0);
            decoder.start();


            decodeEncodeFromSurfaceToSurface(encoder, inputSurface, decoder, outputSurface);

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (inputSurface != null) {
                inputSurface.release();
            }
            if (outputSurface != null) {
                outputSurface.release();
            }
            if (encoder != null) {
                encoder.stop();
                encoder.release();
            }
            if (decoder != null) {
                decoder.stop();
                decoder.release();
            }
            if (mMuxer != null) {
                mMuxer.stop();
                mMuxer.release();
                mMuxer = null;
            }
        }
    }
    /**
     * Returns the first codec capable of encoding the specified MIME type, or null if no
     * match was found.
     */
    private static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
//        MediaCodecInfo[] numCodecs = (new MediaCodecList()).getCodecInfos();
        for (int i = 0; i < numCodecs; i++) {
//        for (MediaCodecInfo codecInfo:numCodecs) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }

            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }

    private void decodeEncodeFromSurfaceToSurface(MediaCodec encoder,
                                                  InputSurface inputSurface, MediaCodec decoder,
                                                  CodecOutputSurface outputSurface) {
        mMediaExtractor.selectTrack(mDecodeTrackIndex);
        final int TIMEOUT_USEC = 10000;
        ByteBuffer[] decoderInputBuffers = decoder.getInputBuffers();
        ByteBuffer[] encoderOutputBuffers = encoder.getOutputBuffers();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean outputDone = false;
        boolean inputDone = false;
        boolean decoderDone = false;
        long presentationTimeUs = 0;
        int encodeFrame = 0;
        int inputChunk = 0; //解码到surface
        while (!outputDone) {
            try {
                if (!inputDone) {
                    int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                    if (inputBufIndex >= 0) {
                        ByteBuffer dstBuf = decoderInputBuffers[inputBufIndex];
                        int sampleSize = mMediaExtractor.readSampleData(dstBuf, 0);
                        if (sampleSize < 0) {
                            Log.e(TAG, "saw input EOS. Stopping decoding");
                            decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            presentationTimeUs = mMediaExtractor.getSampleTime();
                            decoder.queueInputBuffer(inputBufIndex, 0, sampleSize, presentationTimeUs,
                                    inputDone ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                            mMediaExtractor.advance();
                            if (VERBOSE) {
                                Log.d(TAG, "submitted video frame " + (++inputChunk) + " prepare to decode");
                            }
                        }
                    } else {
                        Log.e(TAG, "inputBufIndex " + inputBufIndex);
                    }
                } // !sawInputEOS

                // Assume output is available.  Loop until both assumptions are false.
                boolean decoderOutputAvailable = !decoderDone;
                boolean encoderOutputAvailable = true;
                while (decoderOutputAvailable || encoderOutputAvailable) {
                    // Encoder is drained, check to see if we've got a new frame of output from
                    // the decoder.  (The output is going to a Surface, rather than a ByteBuffer,
                    // but we still get information through BufferInfo.)
                    if (!decoderDone) {
                        int decoderStatus = decoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                        if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                            // no output available yet
                            decoderOutputAvailable = false;
                        } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                            //decoderOutputBuffers = decoder.getOutputBuffers();
                            if (VERBOSE) Log.d(TAG, "decoder output buffers changed (we don't care)");
                        } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            // expected before first buffer of data
                            MediaFormat newFormat = decoder.getOutputFormat();
                            if (VERBOSE) Log.d(TAG, "decoder output format changed: " + newFormat);
                        } else if (decoderStatus < 0) {
                            fail("unexpected result from decoder.dequeueOutputBuffer: " + decoderStatus);
                        } else { // decoderStatus >= 0
                            // The ByteBuffers are null references, but we still get a nonzero
                            if (VERBOSE) Log.d(TAG, "surface decoder given buffer "
                                    + decoderStatus + " (size=" + info.size + ")");
                            // size for the decoded data.
                            boolean doRender = (info.size != 0);
                            // As soon as we call releaseOutputBuffer, the buffer will be forwarded
                            // to SurfaceTexture to convert to a texture.  The API doesn't
                            // guarantee that the texture will be available before the call
                            // returns, so we need to wait for the onFrameAvailable callback to
                            // fire.  If we don't wait, we risk rendering from the previous frame.
                            decoder.releaseOutputBuffer(decoderStatus, doRender);


                            if (doRender) { // This waits for the image and renders it after it arrives.
                                if (VERBOSE) Log.d(TAG, "awaiting frame");
                                outputSurface.awaitNewImage();
                                outputSurface.drawImage(false); // Send it to the encoder.
                                inputSurface.setPresentationTime(info.presentationTimeUs * 1000);
                                if (VERBOSE) Log.d(TAG, "swapBuffers");
                                inputSurface.swapBuffers();
                            }
                            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                // forward decoder EOS to encoder
                                if (VERBOSE) Log.d(TAG, "signaling input EOS");
                                encoder.signalEndOfInputStream();
                            }
                        }
                    }
                    // Start by draining any pending output from the encoder.  It's important to
                    // do this before we try to stuff any more data in.
                    int encoderStatus = encoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                    if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) { // no output available yet
                        if (VERBOSE) Log.d(TAG, "no output from encoder available");
                        encoderOutputAvailable = false;
                    } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        encoderOutputBuffers = encoder.getOutputBuffers();
                        if (VERBOSE) Log.d(TAG, "encoder output buffers changed");
                    } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // should happen before receiving buffers, and should only happen once
                        if (mMuxerStarted) {
                            throw new RuntimeException("format changed twice");
                        }
                        MediaFormat newFormat = encoder.getOutputFormat();
                        Log.d(TAG, "encoder output format changed: " + newFormat);
                        // now that we have the Magic Goodies, start the muxer
                        mEncodeTrackIndex = mMuxer.addTrack(newFormat);
                        mMuxer.start();
                        mMuxerStarted = true;
                    } else if (encoderStatus < 0) {
                        fail("unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
                    } else { // encoderStatus >= 0
                        ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                        if (encodedData == null) {
                            throw new RuntimeException("encoderOutputBuffer " + encoderStatus + " was null");
                        }
                        if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            // The codec config data was pulled out and fed to the muxer when we got
                            // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                            Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                            info.size = 0;
                        }                        // Write the data to the output "file".
                        if (info.size != 0) {
                            encodedData.position(info.offset);
                            encodedData.limit(info.offset + info.size);
                            if (VERBOSE) Log.d(TAG, "encoder output " + info.size + " bytes,frame:" + (++encodeFrame));
                            mMuxer.writeSampleData(mEncodeTrackIndex, encodedData, info);
                        }
                        outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                        encoder.releaseOutputBuffer(encoderStatus, false);
                    }
                }
            } catch (RuntimeException e) {
                Log.e(TAG, " error:" + e.getMessage());

            }
        }

    }

    private void fail(String s) {
    }

    //<editor-fold desc="获取视频文件参数">
    private void extractor(String source){
        mMediaExtractor = new MediaExtractor();
        try {
            mMediaExtractor.setDataSource(source);
            int trackCount = mMediaExtractor.getTrackCount();
            for (int i = 0; i < trackCount; i++) {
                MediaFormat format = mMediaExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("video/")) {
                    mVideoFormat = format;
                    mDecodeTrackIndex = i;
                    mMimeType = mime;
                    break;
                }
            }
            int width = mVideoFormat.getInteger(MediaFormat.KEY_WIDTH);
            int height = mVideoFormat.getInteger(MediaFormat.KEY_HEIGHT);
            mWidth = width;
            mHeight = height;
        } catch (IOException e) {
            mMediaExtractor.release();
        }
    }
    //</editor-fold>
}
