package com.live.audiocast;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

public class AudioRecordUtil {

    //Set the audio sampling rate, 44100 is the current standard, but some devices still support 22050, 16000, 11025
    private final int sampleRateInHz = 44100;
    //Set the audio recording channel CHANNEL_IN_STEREO to dual channel, CHANNEL_CONFIGURATION_MONO to mono
    private final int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
    //Audio data format: PCM 16 bits per sample. Ensure equipment support. PCM 8 bits per sample. It may not be supported by the device.
    private final int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    //Recording status
    private boolean recorderState = true;
    private byte[] buffer;
    private AudioRecord audioRecord;
    private static AudioRecordUtil audioRecordUtil = new AudioRecordUtil();

    public static AudioRecordUtil getInstance() {
        return audioRecordUtil;
    }

    private AudioRecordUtil() {
        init();
    }

    private void init() {
        int recordMinBufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
        //Specify the size of the AudioRecord buffer
        buffer = new byte[recordMinBufferSize];
        //Construct AudioRecord entity object according to recording parameters
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRateInHz, channelConfig,
                audioFormat, recordMinBufferSize);
    }

    /**
     * Start recording
     */
    public void start() {
        if (audioRecord.getState() == AudioRecord.RECORDSTATE_STOPPED) {
            recorderState = true;
            audioRecord.startRecording();
            new RecordThread().start();
        }
    }

    /**
     * Stop recording
     */
    public void stop() {
        recorderState = false;
        if (audioRecord.getState() == AudioRecord.RECORDSTATE_RECORDING) {
            audioRecord.stop();
        }
        audioRecord.release();
    }

    private class RecordThread extends Thread {

        @Override
        public void run() {
            while (recorderState) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                if (AudioRecord.ERROR_INVALID_OPERATION != read) {
                    //The acquired pcm data is the buffer
                    Log.d("TAG", String.valueOf(buffer.length));
                }
            }
        }
    }
}
