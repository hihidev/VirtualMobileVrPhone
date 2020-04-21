package dev.hihi.virtualmobilevrphone;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.util.concurrent.CountDownLatch;

public class AudioEncoder {

    private static final String TAG = "AudioEncoder";

    private static final int SAMPLE_RATE = 44100; // Hz
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final int CHANNEL_MASK = AudioFormat.CHANNEL_IN_STEREO;
    private final int BUFFER_SIZE = 2 * AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_MASK, ENCODING);

    volatile private boolean isRunning = true;
    private CountDownLatch mCountDownLatch = new CountDownLatch(1);

    // TODO: Encode audio, do not just stream PCM directly
    @RequiresApi(api = Build.VERSION_CODES.Q)
    public void streamAudio(final MediaProjection mediaProjection, final MirrorServerInterface server) {

        new Thread () {
            public void run() {
                AudioPlaybackCaptureConfiguration config = new AudioPlaybackCaptureConfiguration.Builder(
                        mediaProjection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                        .build();
                AudioFormat audioFormat = new AudioFormat.Builder()
                        .setEncoding(ENCODING)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_MASK)
                        .build();
                AudioRecord audioRecord = new AudioRecord.Builder()
                        .setAudioFormat(audioFormat)
                        .setBufferSizeInBytes(BUFFER_SIZE)
                        .setAudioPlaybackCaptureConfig(config)
                        .build();

                try {
                    byte[] buffer = new byte[BUFFER_SIZE];

                    audioRecord.startRecording();
                    while (isRunning) {
                        int read = audioRecord.read(buffer, 0, buffer.length);
                        server.sendBuf(buffer, read);
                    }
                } finally {
                    try {
                        if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                            audioRecord.stop();
                        }
                    } catch (IllegalStateException e) {
                        e.printStackTrace();
                    }
                    if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                        audioRecord.release();
                    }
                    mCountDownLatch.countDown();
                }
            }
        }.start();
    }

    public void stop() {
        isRunning = false;
    }

    public void waitUntilStopped() {
        try {
            mCountDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
