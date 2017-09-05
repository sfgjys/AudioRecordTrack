package com.myself.audiorecordtrack;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import java.util.LinkedList;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    /**
     * 变量的描述: 使用AudioRecord采集PCM音频数据的线程
     */
    private Thread mPlayPCMThread;
    /**
     * 变量的描述: 使用AudioTrack播放PCM音频数据的线程
     */
    private Thread mCollectPCMThread;
    /**
     * 变量的描述: 线程中的程序是否继续循环，值为false则循环停止，线程也走完结束了
     */
    private boolean mThreadRoutineIsContinue = true;
    /**
     * 变量的描述: 采集PCM最小缓冲区的大小
     */
    private int mCollectMinBufferSize;
    /**
     * 变量的描述: 采集麦克风收集的PCM数据的工具
     */
    private AudioRecord mCollectPCMTool;
    /**
     * 变量的描述: 播放PCM最小缓冲区的大小
     */
    private int mPlayMinBufferSize;
    /**
     * 变量的描述: 播放PCM数据的工具
     */
    private AudioTrack mPlayPCMTool;
    /**
     * 变量的描述:
     */
    private byte[] mCollectByte;
    /**
     * 变量的描述:
     */
    private byte[] mPlayByte;
    /**
     * 变量的描述: 在采集音频时用来存储两个缓存byte[]，当播放时可直接使用第一个缓存byte[]（第一个缓存byte[]，只有在获取到第三个缓存byte[]时，才会去除掉）
     */
    private LinkedList<byte[]> mListByte;

    // --------------------------------------------------------------------------------------------------------

    /**
     * 变量的描述: 音频采集频率
     */
    private final int mSamplingFrequency = 32000;
    /**
     * 变量的描述: 声道类型(单声道或者双声道) AudioFormat.CHANNEL_IN_STEREO 和 AudioFormat.CHANNEL_OUT_STEREO 值一样
     */
    private final int mChannelType = AudioFormat.CHANNEL_IN_STEREO;
    /**
     * 变量的描述: 采集音频的比特率
     */
    private final int mBitRate = AudioFormat.ENCODING_PCM_16BIT;
    /**
     * 变量的描述: 录音的声源
     */
    private final int mRecordingSoundSource = MediaRecorder.AudioSource.MIC;
    /**
     * 变量的描述: 音频流的类型  STREAM_VOICE_CALL, STREAM_SYSTEM, STREAM_RING, STREAM_MUSIC, STREAM_ALARM, STREAM_NOTIFICATION.
     */
    private final int mAudioStreamType = AudioManager.STREAM_MUSIC;
    /**
     * 变量的描述: 播放的数据类型 流或者静态的缓冲区 播放是实时的还是静态的数据
     */
    private final int mPlayDataType = AudioTrack.MODE_STREAM;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();

        initThread();
    }

    private void initTool() {
        // AudioRecord 得到录制最小缓冲区的大小
        mCollectMinBufferSize = AudioRecord.getMinBufferSize(mSamplingFrequency, mChannelType, mBitRate);
        // 实例化采集麦克风收集的PCM数据的工具
        mCollectPCMTool = new AudioRecord(mRecordingSoundSource, mSamplingFrequency, mChannelType, mBitRate, mCollectMinBufferSize);

        // --------------------------------------------------------------------------------------------------------

        // AudioTrack 得到播放最小缓冲区的大小
        mPlayMinBufferSize = AudioTrack.getMinBufferSize(mSamplingFrequency, mChannelType, mBitRate);
        // 实例化播放音频对象
        mPlayPCMTool = new AudioTrack(mAudioStreamType, mSamplingFrequency, mChannelType, mBitRate, mPlayMinBufferSize, mPlayDataType);
    }

    private void initView() {
        findViewById(R.id.start).setOnClickListener(this);
        findViewById(R.id.end).setOnClickListener(this);
    }

    private void initThread() {
        mCollectPCMThread = new Thread(new CollectPCM());
        mPlayPCMThread = new Thread(new PlayPCM());
    }

    private void initDataSave() {

        // 实例化一个字节数组，长度为最小缓冲区的长度
        mCollectByte = new byte[mCollectMinBufferSize];
        // 实例化一个链表，用来存放字节组数
        mListByte = new LinkedList<>();

        // --------------------------------------------------------------------------------------------------------

        // 实例化一个长度为播放最小缓冲大小的字节数组
        mPlayByte = new byte[mPlayMinBufferSize];
    }

    private void clearData() {
        if (mListByte != null) {
            mListByte.clear();
            mListByte = null;
        }
        mCollectByte = null;
        mPlayByte = null;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.start:
                initTool();
                initDataSave();
                mThreadRoutineIsContinue = true;

                // 启动采集PCM线程
                mCollectPCMThread.start();
                // 启动播放线程
                mPlayPCMThread.start();
                break;
            case R.id.end:
                mThreadRoutineIsContinue = false;
                mCollectPCMTool.stop();
                mCollectPCMTool = null;
                mPlayPCMTool.stop();
                mPlayPCMTool = null;
                clearData();
                break;
        }
    }

    private class CollectPCM implements Runnable {
        @Override
        public void run() {
            // 在mCollectByte获取到了新的录音数据缓存时，克隆出一个byte[]给collectMiddleBytes，然后将collectMiddleBytes放入链表集合中供播放使用
            byte[] collectMiddleBytes;
            // 开始录音
            mCollectPCMTool.startRecording();

            while (mThreadRoutineIsContinue) {
                mCollectPCMTool.read(mCollectByte, 0, mCollectMinBufferSize);
                collectMiddleBytes = mCollectByte.clone();
                if (mListByte.size() >= 2) {
                    mListByte.removeFirst();
                }
                mListByte.add(collectMiddleBytes);
            }
        }
    }

    private class PlayPCM implements Runnable {
        @Override
        public void run() {
            byte[] playMiddle;
            // 开始播放
            mPlayPCMTool.play();

            while (mThreadRoutineIsContinue) {
                try {
                    mPlayByte = mListByte.getFirst();
                    playMiddle = mPlayByte.clone();
                    mPlayPCMTool.write(playMiddle, 0, playMiddle.length);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
