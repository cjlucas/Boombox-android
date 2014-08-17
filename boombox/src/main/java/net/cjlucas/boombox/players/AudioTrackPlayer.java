package net.cjlucas.boombox.players;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by chris on 8/16/14.
 */
public class AudioTrackPlayer extends Player
        implements AudioTrack.OnPlaybackPositionUpdateListener {
    private static final String TAG = "AudioTrackPlayer";

    private AudioDecoder mDecoder;
    private AudioTrack mAudioTrack;

    public AudioTrackPlayer(String dataSource) {
        super(dataSource);
        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                44100,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                AudioTrack.getMinBufferSize(44100,
                        AudioFormat.CHANNEL_OUT_STEREO,
                        AudioFormat.ENCODING_PCM_16BIT),
                AudioTrack.MODE_STREAM);

        mAudioTrack.setPlaybackPositionUpdateListener(this);
        mAudioTrack.setPositionNotificationPeriod(10);
    }

    @Override
    public boolean prepare() {
        MediaExtractor extractor = new MediaExtractor();

        try {
            extractor.setDataSource(getDataSource());
        } catch (IOException e) {
            Log.e(TAG, "Could not prepare", e);
            return false;
        }

        mDecoder = new AudioDecoder(extractor, mAudioTrack);
        mDecoder.start();
        return true;
    }

    @Override
    public void play() {
        mAudioTrack.play();
    }

    @Override
    public void pause() {
        mAudioTrack.pause();
    }

    @Override
    public void stop() {
        mAudioTrack.pause();
        mAudioTrack.flush();
        mDecoder.halt();
    }

    @Override
    public void onMarkerReached(AudioTrack track) {
    }

    @Override
    public void onPeriodicNotification(AudioTrack track) {
        System.out.println("onPeriodicNotification: " + track.getPlaybackHeadPosition());
    }

    private static class AudioDecoder extends Thread {
        private MediaExtractor mExtractor;
        private AudioTrack mAudioTrack;
        private boolean mShouldHalt;

        public AudioDecoder(MediaExtractor extractor, AudioTrack audioTrack) {
            mExtractor = extractor;
            mAudioTrack = audioTrack;
            mShouldHalt = false;
        }

        public void halt() {
            mShouldHalt = true;
        }

        @Override
        public void run() {
            MediaFormat mediaFormat = mExtractor.getTrackFormat(0);
            System.out.println(mediaFormat.getString(MediaFormat.KEY_MIME));

            mExtractor.selectTrack(0);

            MediaCodec codec = MediaCodec.createDecoderByType(
                    mediaFormat.getString(MediaFormat.KEY_MIME));

            codec.configure(mediaFormat, null, null, 0);
            codec.start();

            ByteBuffer[] codecInputBuffers = codec.getInputBuffers();
            ByteBuffer[] codecOutputBuffers = codec.getOutputBuffers();

            boolean sawInputEOS = false;

            while (!sawInputEOS && !mShouldHalt) {
                int inputBufferIndex = codec.dequeueInputBuffer(-1);

                ByteBuffer dstBuf = codecInputBuffers[inputBufferIndex];

                int sampleSize = mExtractor.readSampleData(dstBuf, 0);
                long presentationTimeUs = 0;

                if (sampleSize < 0) {
                    System.out.println("sample size less than 0 (" + sampleSize + ")");
                    sawInputEOS = true;
                    sampleSize = 0;
                } else {
                    presentationTimeUs = mExtractor.getSampleTime();
                }

                codec.queueInputBuffer(inputBufferIndex,
                        0, //offset
                        sampleSize,
                        presentationTimeUs,
                        sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);

                if (!sawInputEOS) {
                    mExtractor.advance();
                }

                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                int outputBufferIndex = codec.dequeueOutputBuffer(info, -1);

                switch (outputBufferIndex) {
                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                        System.out.println("hereee");
                        Integer newSampleRate = codec.getOutputFormat()
                                .getInteger(MediaFormat.KEY_SAMPLE_RATE);
                        System.out.println(newSampleRate);
                        mAudioTrack.setPlaybackRate(newSampleRate);
                        continue;
                    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                        codecOutputBuffers = codec.getOutputBuffers();
                        continue;
                    default:
                        break;
                }


                ByteBuffer buf = codecOutputBuffers[outputBufferIndex];
                byte[] chunk = new byte[info.size];
                buf.get(chunk);
                buf.clear();

                mAudioTrack.write(chunk, 0, chunk.length);
                codec.releaseOutputBuffer(outputBufferIndex, false);
            }

            mExtractor.release();
        }
    }
}
