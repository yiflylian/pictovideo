package com.me.picdemo;

import android.graphics.Bitmap;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.util.Log;

/**
 * @author simpler
 * @create 2020年05月21日  16:22
 */
class MediaUtils {
    public int[] getMediaCodecList() {
        //获取解码器列表
        int numCodecs = MediaCodecList.getCodecCount();
        MediaCodecInfo codecInfo = null;
        for (int i = 0; i < numCodecs && codecInfo == null; i++) {
            MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
            if (!info.isEncoder()) {
                continue;
            }
            String[] types = info.getSupportedTypes();
            boolean found = false;
            //轮训所要的解码器
            for (int j = 0; j < types.length && !found; j++) {
                if (types[j].equals("video/avc")) {
                    found = true;
                }
            }
            if (!found) {
                continue;
            }
            codecInfo = info;
        }
        Log.e("ok", "found" + codecInfo.getName() + "supporting" + " video/avc");
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType("video/avc");
        return capabilities.colorFormats;
    }
    public int getColorFormat(){
        int colorFormat =0;
        int[] formats = this.getMediaCodecList();

        lab:
        for (int format : formats) {
            switch (format) {
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar: // yuv420sp
                    colorFormat = format;
                    break lab;
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar: // yuv420p
                    colorFormat = format;
                    break lab;
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar: // yuv420psp
                    colorFormat = format;
                    break lab;
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar: // yuv420pp
                    colorFormat = format;
                    break lab;
            }
        }

        if (colorFormat <= 0) {
            colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
        }
        return colorFormat;
    }

    private void encodeYUV420SP(byte[] yuv420sp, int[] argb, int width, int height) {
        final int frameSize = width * height;

        int yIndex = 0;
        int uvIndex = frameSize;

        int a, R, G, B, Y, U, V;
        int index = 0;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {

                a = (argb[index] & 0xff000000) >> 24; // a is not used obviously
                R = (argb[index] & 0xff0000) >> 16;
                G = (argb[index] & 0xff00) >> 8;
                B = (argb[index] & 0xff) >> 0;

                // well known RGB to YUV algorithm
                Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                V = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128; // Previously U
                U = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128; // Previously V

                yuv420sp[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                if (j % 2 == 0 && index % 2 == 0) {
                    yuv420sp[uvIndex++] = (byte) ((V < 0) ? 0 : ((V > 255) ? 255 : V));
                    yuv420sp[uvIndex++] = (byte) ((U < 0) ? 0 : ((U > 255) ? 255 : U));
                }

                index++;
            }
        }
    }

    private void encodeYUV420P(byte[] yuv420sp, int[] argb, int width, int height) {
        final int frameSize = width * height;

        int yIndex = 0;
        int uIndex = frameSize;
        int vIndex = frameSize + width * height / 4;

        int a, R, G, B, Y, U, V;
        int index = 0;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {

                a = (argb[index] & 0xff000000) >> 24; // a is not used obviously
                R = (argb[index] & 0xff0000) >> 16;
                G = (argb[index] & 0xff00) >> 8;
                B = (argb[index] & 0xff) >> 0;

                // well known RGB to YUV algorithm
                Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                V = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128; // Previously U
                U = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128; // Previously V

                yuv420sp[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                if (j % 2 == 0 && index % 2 == 0) {
                    yuv420sp[vIndex++] = (byte) ((U < 0) ? 0 : ((U > 255) ? 255 : U));
                    yuv420sp[uIndex++] = (byte) ((V < 0) ? 0 : ((V > 255) ? 255 : V));
                }

                index++;
            }
        }
    }
    private byte[] getNV12(int colorFormat,int inputWidth, int inputHeight, Bitmap scaled) {

        int[] argb = new int[inputWidth * inputHeight];

        //Log.i(TAG, "scaled : " + scaled);
        scaled.getPixels(argb, 0, inputWidth, 0, 0, inputWidth, inputHeight);

        byte[] yuv = new byte[inputWidth * inputHeight * 3 / 2];

        switch (colorFormat) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar: // yuv420sp
                encodeYUV420SP(yuv, argb, inputWidth, inputHeight);
                break;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar: // yuv420p
                encodeYUV420P(yuv, argb, inputWidth, inputHeight);
                break;
//            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar: // yuv420psp
//                encodeYUV420PSP(yuv, argb, inputWidth, inputHeight);
//                break;
//            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar: // yuv420pp
//                encodeYUV420PP(yuv, argb, inputWidth, inputHeight);
//                break;
        }
//        scaled.recycle();

        return yuv;
    }
}
