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
     * 变量的描述:
     */
    private LinkedList<byte[]> mListByte;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();

        initTool();

        initThread();

    }

    private void initTool() {
        // AudioRecord 得到录制最小缓冲区的大小
        mCollectMinBufferSize = AudioRecord.getMinBufferSize(8000, AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT);
        // 实例化采集麦克风收集的PCM数据的工具
        mCollectPCMTool = new AudioRecord(MediaRecorder.AudioSource.MIC, 8000, AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT, mCollectMinBufferSize);

        // --------------------------------------------------------------------------------------------------------

        // 实例化一个字节数组，长度为最小缓冲区的长度
        mCollectByte = new byte[mCollectMinBufferSize];
        // 实例化一个链表，用来存放字节组数
        mListByte = new LinkedList<byte[]>();

        // --------------------------------------------------------------------------------------------------------

        // AudioTrack 得到播放最小缓冲区的大小
        mPlayMinBufferSize = AudioTrack.getMinBufferSize(8000, AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT);
        // 实例化播放音频对象
        mPlayPCMTool = new AudioTrack(AudioManager.STREAM_MUSIC, 8000, AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT, mPlayMinBufferSize, AudioTrack.MODE_STREAM);

        // --------------------------------------------------------------------------------------------------------

        // 实例化一个长度为播放最小缓冲大小的字节数组
        mPlayByte = new byte[mPlayMinBufferSize];
    }

    private void initView() {
        findViewById(R.id.start).setOnClickListener(this);
        findViewById(R.id.end).setOnClickListener(this);
    }

    private void initThread() {
        mCollectPCMThread = new Thread(new CollectPCM());
        mPlayPCMThread = new Thread(new PlayPCM());
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.start:
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
                break;
        }
    }

    private class CollectPCM implements Runnable {
        @Override
        public void run() {
            byte[] bytes_pkg;
            // 开始录音
            mCollectPCMTool.startRecording();

            while (mThreadRoutineIsContinue) {
                mCollectPCMTool.read(mCollectByte, 0, mCollectMinBufferSize);
                bytes_pkg = mCollectByte.clone();
                if (mListByte.size() >= 2) {
                    mListByte.removeFirst();
                }
                mListByte.add(bytes_pkg);
            }
        }
    }

    private class PlayPCM implements Runnable {
        @Override
        public void run() {
            byte[] bytes_pkg = null;
            // 开始播放
            mPlayPCMTool.play();

            while (mThreadRoutineIsContinue) {
                try {
                    mPlayByte = mListByte.getFirst();
                    bytes_pkg = mPlayByte.clone();
                    mPlayPCMTool.write(bytes_pkg, 0, bytes_pkg.length);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
