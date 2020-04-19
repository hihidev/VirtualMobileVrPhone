package dev.hihi.virtualmobilevrphone;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

public class VideoEncoder {

    private static final String TAG = "VideoEncoder";

    private static final float I_FRAME_INTERVAL = 5f; // seconds
    private static final int REPEAT_FRAME_DELAY_US = 100_000; // repeat after 100ms
    private static final int BIT_RATE = 8_000_000;
    private static final int MAX_FPS = 60;
    private static final String MIME_TYPE = "video/avc";
    private static final int CODE_PROFILE = MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline;
    private static final int CODE_PROFILE_LEVEL = MediaCodecInfo.CodecProfileLevel.AV1Level2;
    private static final int MAX_PTS_GAP_TO_ENCODER = -1000*20; // us

    private byte[] sps;
    private byte[] pps;

    private int width;
    private int height;

    private boolean isStopped = false;
    volatile private boolean mClientConnected = false;

    public void start(MediaProjection mediaProjection, int width, int height, int density,
            MirrorServerInterface server) {

        this.width = width;
        this.height = height;

        while (!mClientConnected && !isStopped) {
            SystemClock.sleep(50);
        }
        if (isStopped) {
            return;
        }

        MediaFormat format = createFormat(width, height);
        try {
            MediaCodec codec = MediaCodec.createEncoderByType(MIME_TYPE);
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            Surface surface = codec.createInputSurface();
            VirtualDisplay virtualDisplay = mediaProjection.createVirtualDisplay(
                    "SCREENCAP_NAME", width, height, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, surface, null, null);
            try {
                encode(codec, server);
            } finally {
                codec.stop();
                if (virtualDisplay != null) {
                    virtualDisplay.release();
                }
                codec.release();
                surface.release();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private MediaFormat createFormat(int width, int height) {
        final MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, MIME_TYPE);
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        format.setInteger(MediaFormat.KEY_PROFILE, CODE_PROFILE);
        format.setInteger(MediaFormat.KEY_LEVEL, CODE_PROFILE_LEVEL);
        format.setInteger(MediaFormat.KEY_MAX_PTS_GAP_TO_ENCODER, MAX_PTS_GAP_TO_ENCODER);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);
        // display the very first frame, and recover from bad quality when no new frames
        format.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, REPEAT_FRAME_DELAY_US); // µs
        format.setFloat(MediaFormat.KEY_MAX_FPS_TO_ENCODER, MAX_FPS);
        format.setInteger(MediaFormat.KEY_WIDTH, width);
        format.setInteger(MediaFormat.KEY_HEIGHT, height);

        return format;
    }

    private boolean encode(MediaCodec codec, final MirrorServerInterface server) {
        Log.i(TAG, "encode()");
        boolean eof = false;
        boolean needToSendSps = true;

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        codec.start();

        while (!eof && !isStopped) {
            int outputBufferId = codec.dequeueOutputBuffer(bufferInfo, 100_000);
            eof = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;

            try {
                if (outputBufferId >= 0) {
                    ByteBuffer codecBuffer = codec.getOutputBuffer(outputBufferId);
                    byte[] outData = new byte[bufferInfo.size];
                    codecBuffer.get(outData);

                    if (sps == null || pps == null) {
                        ByteBuffer spsPpsBuffer = ByteBuffer.wrap(outData);
                        spsPpsBuffer.position(4);
                        int ppsIndex = 0;
                        while (!(spsPpsBuffer.get() == 0x00 && spsPpsBuffer.get() == 0x00
                                && spsPpsBuffer.get() == 0x00 && spsPpsBuffer.get() == 0x01)) {
                        }
                        ppsIndex = spsPpsBuffer.position() - 4;
                        sps = new byte[ppsIndex];
                        System.arraycopy(outData, 0, sps, 0, sps.length);
                        pps = new byte[outData.length - ppsIndex];
                        System.arraycopy(outData, ppsIndex, pps, 0, pps.length);

                        printBytes("sps", sps);
                        printBytes("pps", pps);

                        continue;
                    }

                    if (server.isConnected()) {
                        if (needToSendSps) {
                            // Only send sps pps with key frame
                            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                                byte[] buf = new byte[4];
                                buf[0] = (byte) ((width >> 8) & 0xff);
                                buf[1] = (byte) ((width) & 0xff);
                                buf[2] = (byte) ((height >> 8) & 0xff);
                                buf[3] = (byte) ((height) & 0xff);
                                server.sendBuf(buf, buf.length);

                                buf = new byte[outData.length + sps.length + pps.length];
                                System.arraycopy(sps, 0, buf, 0, sps.length);
                                System.arraycopy(pps, 0, buf, sps.length, pps.length);
                                System.arraycopy(outData, 0, buf, sps.length + pps.length,
                                        outData.length);
                                server.sendBuf(buf, buf.length);

                                needToSendSps = false;
                            } else {
                                // If it's not a key frame, request one, until we send sps pps
                                // with key frame.
                                final Bundle syncFrame = new Bundle();
                                syncFrame.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
                                codec.setParameters(syncFrame);
                            }
                        } else {
                            server.sendBuf(outData, outData.length);
                        }
                    } else {
                        needToSendSps = true;
                    }
                }
            } finally {
                if (outputBufferId >= 0) {
                    codec.releaseOutputBuffer(outputBufferId, false);
                }
            }
        }
        return !eof;
    }

    public void stop() {
        isStopped = true;
    }

    private static void printBytes(String tag, byte[] bytes) {
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            buf.append(Integer.toHexString(bytes[i] & 0xff)).append(" ");
        }
        Log.i(TAG, "tag: " + tag + ", bytes: " + buf);
    }

    public void onClientConnected() {
        mClientConnected = true;
    }
}
