package com.live.audiocast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    private static final String LOG_TAG = "log_tag";
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

    AudioCastServer server = null;
    public static Context context = null;

    private byte[] header = new byte[44];

    boolean m_bRecording = true;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.context = this;

        if (Build.VERSION.SDK_INT >= 23) {
            String[] permissions = {Manifest.permission.RECORD_AUDIO};
            if (!hasPermissionsGranted(permissions)) {
                ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);
                return;
            }
        }

        server = new AudioCastServer(9001);
        server.start();

        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        String ip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
        TinyWebServer.startServer(ip,9000, "/web/public_html");
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        //stop webserver on destroy of service or process
        m_bRecording = false;

        try {
            server.stop();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        TinyWebServer.stopServer();
    }

    private boolean hasPermissionsGranted(String[] permissions) {
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public void onStartRecording(View v)
    {
        startRecording();
    }

    private AudioRecord recorder = null;
    int channel = 2;
    int bitPerSample = 16;
    int sampleRate = 44100;
    byte buffer[] = null;
    private PCMEncoderAAC pcmEncoderAAC;

//    private AudioTrack mAudioTrack;

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

    private void startRecording()
    {
        pcmEncoderAAC = new PCMEncoderAAC(sampleRate, new PCMEncoderAAC.EncoderListener() {
            @Override
            public void encodeAAC(byte[] data) {
                server.broadcast(data);
            }
        });

        int buffer_size = sampleRate * channel * bitPerSample / 40; // 200ms
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                buffer_size
        );

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

    private void stopRecording()
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
//                    Log.d("TAG", String.valueOf(buffer.toString()));
                    server.broadcast(buffer);
//                    pcmEncoderAAC.encodeData(buffer);
                }
            }
        }

        if( recorder != null ) {
            recorder.stop();
            recorder.release();
            recorder = null;
        }
    }


}