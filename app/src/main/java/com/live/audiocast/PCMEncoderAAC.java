package com.live.audiocast;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;

import java.io.IOException;
import java.nio.ByteBuffer;

public class PCMEncoderAAC {

    //Bit rate
    private final static int KEY_BIT_RATE = 96000;
    //The maximum number of bytes of data read
    private final static int KEY_MAX_INPUT_SIZE = 1024 * 1024;
    //Number of channels
    private final static int CHANNEL_COUNT = 2;
    private MediaCodec mediaCodec;
    private ByteBuffer[] encodeInputBuffers;
    private ByteBuffer[] encodeOutputBuffers;
    private MediaCodec.BufferInfo encodeBufferInfo;
    private EncoderListener encoderListener;

    public PCMEncoderAAC(int sampleRate, EncoderListener encoderListener) {
        this.encoderListener = encoderListener;
        init(sampleRate);
    }

    /**
     * Initialize the AAC encoder
     */
    private void init(int sampleRate) {
        try {
            //Parameter correspondence -> mime type, sampling rate, number of channels
            MediaFormat encodeFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,
                    sampleRate, CHANNEL_COUNT);
            //Bit rate
            encodeFormat.setInteger(MediaFormat.KEY_BIT_RATE, KEY_BIT_RATE);
            encodeFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            encodeFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, KEY_MAX_INPUT_SIZE);
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            mediaCodec.configure(encodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch ( IOException e) {
            e.printStackTrace();
        }

        mediaCodec.start();
        encodeInputBuffers = mediaCodec.getInputBuffers();
        encodeOutputBuffers = mediaCodec.getOutputBuffers();
        encodeBufferInfo = new MediaCodec.BufferInfo();
    }

    /**
     * @param data
     */
    public void encodeData(byte[] data) {
        //dequeueInputBuffer(time) needs to pass in a time value, -1 means waiting forever, 0 means not waiting, there may be frame loss, others means how many milliseconds to wait
        //Get the index of the input buffer
        int inputIndex = mediaCodec.dequeueInputBuffer(-1);
        if (inputIndex >= 0) {
            ByteBuffer inputByteBuf = encodeInputBuffers[inputIndex];
            inputByteBuf.clear();
            //adding data
            inputByteBuf.put(data);
            //Limit the access length of ByteBuffer
            inputByteBuf.limit(data.length);
            //Push the input buffer back to MediaCodec
            mediaCodec.queueInputBuffer(inputIndex, 0, data.length, 0, 0);
        }
        //Get the index of the output cache
        int outputIndex = mediaCodec.dequeueOutputBuffer(encodeBufferInfo, 0);
        while (outputIndex >= 0) {
            //Get the length of the cache information
            int byteBufSize = encodeBufferInfo.size;
            //Add the length after the ADTS header
            int bytePacketSize = byteBufSize + 7;
            //Get the output Buffer
            ByteBuffer outPutBuf = encodeOutputBuffers[outputIndex];
            outPutBuf.position(encodeBufferInfo.offset);
            outPutBuf.limit(encodeBufferInfo.offset + encodeBufferInfo.size);

            byte[] aacData = new byte[bytePacketSize];
            //Add ADTS header
            addADTStoPacket(aacData, bytePacketSize);
            /*
                         get(byte[] dst, int offset, int length): ByteBuffer is read from the position, length bytes are read, and written to dst
                         Mark the area from offset to offset + length
             */
            outPutBuf.get(aacData, 7, byteBufSize);
            outPutBuf.position(encodeBufferInfo.offset);

            //Encoding success
            if (encoderListener != null) {
                encoderListener.encodeAAC(aacData);
            }

            //freed
            mediaCodec.releaseOutputBuffer(outputIndex, false);
            outputIndex = mediaCodec.dequeueOutputBuffer(encodeBufferInfo, 0);
        }
    }

    /**
     * Add ADTS header
     */
    private void addADTStoPacket(byte[] packet, int packetLen) {
        // AAC LC
        int profile = 2;
        // 44.1KHz
        int freqIdx = 4;
        // CPE
        int chanCfg = 2;
        // fill in ADTS data
        packet[0] = (byte) 0xFF;
        packet[1] = (byte) 0xF9;
        packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
        packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;
    }

    public interface EncoderListener {
        void encodeAAC(byte[] data);
    }
}
