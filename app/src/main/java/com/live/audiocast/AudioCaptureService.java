package com.live.audiocast;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import java.io.IOException;

public class AudioCaptureService extends Service {
    private static final String LOG_TAG = "log_tag";

    MediaProjectionManager mediaProjectionManager;
    MediaProjection mediaProjection = null;
    private AudioRecord recorder = null;

    private static final int SERVICE_ID = 123;
    private static final String NOTIFICATION_CHANNEL_ID = "AudioCapture channel";
    public static final String EXTRA_RESULT_DATA = "AudioCaptureService:Extra:ResultData";

    int channel = 2;
    int bitPerSample = 16;
    int sampleRate = 44100;
    byte buffer[] = null;
    private boolean m_bRecording = false;

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();
        startForeground(SERVICE_ID, new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID).build());

        mediaProjectionManager = (MediaProjectionManager) getApplicationContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, "Audio Capture Service Channel", NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManager manager = (NotificationManager) getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        Log.d(LOG_TAG, "onStartCommand = " + action);

        if( action.equals("AudioCaptureService:Start") ) {
            mediaProjection = mediaProjectionManager.getMediaProjection(Activity.RESULT_OK, (Intent) intent.getParcelableExtra(EXTRA_RESULT_DATA));

            startRecording();
            return START_STICKY;
        }

        if( action.equals("AudioCaptureService:Stop") ) {
            stopProc();
            return START_NOT_STICKY;
        }

        return START_NOT_STICKY;
    }

    private void generateWavHeader(int size, int channel, int bitPerSample, int longSampleRate)
    {
        int totalDataLen = 44 - 8 + size;
        int byteRate = longSampleRate * channel * bitPerSample / 8;

        buffer[0] = 'R';  // RIFF/WAVE header
        buffer[1] = 'I';
        buffer[2] = 'F';
        buffer[3] = 'F';
        buffer[4] = (byte) (totalDataLen & 0xff);
        buffer[5] = (byte) ((totalDataLen >> 8) & 0xff);
        buffer[6] = (byte) ((totalDataLen >> 16) & 0xff);
        buffer[7] = (byte) ((totalDataLen >> 24) & 0xff);
        buffer[8] = 'W';
        buffer[9] = 'A';
        buffer[10] = 'V';
        buffer[11] = 'E';
        buffer[12] = 'f';  // 'fmt ' chunk
        buffer[13] = 'm';
        buffer[14] = 't';
        buffer[15] = ' ';
        buffer[16] = 16;  // 4 bytes: size of 'fmt ' chunk
        buffer[17] = 0;
        buffer[18] = 0;
        buffer[19] = 0;
        buffer[20] = 1;  // format = 1
        buffer[21] = 0;
        buffer[22] = (byte) channel;
        buffer[23] = 0;
        buffer[24] = (byte) (longSampleRate & 0xff);
        buffer[25] = (byte) ((longSampleRate >> 8) & 0xff);
        buffer[26] = (byte) ((longSampleRate >> 16) & 0xff);
        buffer[27] = (byte) ((longSampleRate >> 24) & 0xff);
        buffer[28] = (byte) (byteRate & 0xff);
        buffer[29] = (byte) ((byteRate >> 8) & 0xff);
        buffer[30] = (byte) ((byteRate >> 16) & 0xff);
        buffer[31] = (byte) ((byteRate >> 24) & 0xff);
        buffer[32] = (byte) (2 * 16 / 8);  // block align
        buffer[33] = 0;
        buffer[34] = (byte)bitPerSample;  // bits per sample
        buffer[35] = 0;
        buffer[36] = 'd';
        buffer[37] = 'a';
        buffer[38] = 't';
        buffer[39] = 'a';
        buffer[40] = (byte) (size & 0xff);
        buffer[41] = (byte) ((size >> 8) & 0xff);
        buffer[42] = (byte) ((size >> 16) & 0xff);
        buffer[43] = (byte) ((size >> 24) & 0xff);
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void startRecording()
    {
        int buffer_size = sampleRate * channel * bitPerSample / 50; // 200ms

        AudioPlaybackCaptureConfiguration.Builder builder = new AudioPlaybackCaptureConfiguration.Builder(mediaProjection);
        AudioPlaybackCaptureConfiguration config = builder.addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .build();

        AudioRecord.Builder audio_builder = new AudioRecord.Builder();

        AudioFormat format = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .setSampleRate(sampleRate)
                .build();

        recorder = audio_builder.setAudioFormat(format)
                .setBufferSizeInBytes(buffer_size)
                .setAudioPlaybackCaptureConfig(config)
                .build();


        buffer = new byte[buffer_size + 44];
        generateWavHeader(buffer_size, channel, bitPerSample, sampleRate );

        Thread recordingThread = new Thread(new Runnable() {
            public void run() {
                recordTVData();
            }
        }, "AudioRecorder Thread");

        m_bRecording = true;
        recordingThread.start();
    }

    private void stopProc()
    {
        m_bRecording = false;
    }

    private void recordTVData() {
        Log.d(LOG_TAG, "recordBeepData started");


        // wait record is ready
        while( recorder != null && recorder.getState() == 0)
        {
            try {
                Log.d(LOG_TAG, "Audio Record is not ready");
                Thread.sleep(100L);
            } catch (InterruptedException e) {
            }
        }

        if( recorder != null )
            recorder.startRecording();

        while (m_bRecording) {
            if( recorder != null ) {
                int read = recorder.read(buffer, 44, buffer.length - 44);

                if (AudioRecord.ERROR_INVALID_OPERATION != read) {
                    MainActivity.server.broadcast(buffer);
                }
            }
        }

        if( recorder != null ) {
            recorder.stop();
            recorder.release();
            recorder = null;
        }

        if( mediaProjection != null )
            mediaProjection.stop();
    }
}
