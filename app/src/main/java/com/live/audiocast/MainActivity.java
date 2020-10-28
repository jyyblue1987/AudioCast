package com.live.audiocast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
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
        TinyWebServer.startServer("192.168.0.108",9000, "/web/public_html");
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
    int BufferElements2Rec = 2048; // want to play 2048 (2K) since 2 bytes we use only 1024
    int BytesPerElement = 2; // 2 bytes in 16bit format

    private void startRecording()
    {
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                44100,
                AudioFormat.CHANNEL_CONFIGURATION_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                BufferElements2Rec * BytesPerElement
        );

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
        byte data[] = new byte[BufferElements2Rec];

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
                recorder.read(data, 0, BufferElements2Rec);
                server.broadcast(data);
            }
        }

        if( recorder != null ) {
            recorder.stop();
            recorder.release();
            recorder = null;
        }
    }


}