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
    int sampleRate = 44100;
    byte buffer[] = null;
    private PCMEncoderAAC pcmEncoderAAC;

//    private AudioTrack mAudioTrack;


    private void startRecording()
    {
        pcmEncoderAAC = new PCMEncoderAAC(sampleRate, new PCMEncoderAAC.EncoderListener() {
            @Override
            public void encodeAAC(byte[] data) {
                server.broadcast(data);
            }
        });

//        int recordMinBufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        int buffer_size = sampleRate * 4;
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                buffer_size
        );
        buffer = new byte[buffer_size];

        Thread recordingThread = new Thread(new Runnable() {
            public void run() {
                recordTVData();
            }
        }, "AudioRecorder Thread");

//        mAudioTrack =
//                new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT,
//                        buffer_size, AudioTrack.MODE_STREAM);
//        while( true )
//        {
//            if( mAudioTrack.getState() == mAudioTrack.STATE_INITIALIZED)
//                break;
//            try {
//                Thread.sleep(100);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//                Log.d(LOG_TAG, "AudioTrack Error = " + e.getMessage());
//            }
//        }
//
//        mAudioTrack.play();

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
                int read = recorder.read(buffer, 0, buffer.length);

                if (AudioRecord.ERROR_INVALID_OPERATION != read) {
//                    mAudioTrack.write(buffer, 0, buffer.length);

                    //The acquired pcm data is the buffer
                    Log.d("TAG", String.valueOf(buffer.length));
                    pcmEncoderAAC.encodeData(buffer);
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