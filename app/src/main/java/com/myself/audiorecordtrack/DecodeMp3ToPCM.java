package com.myself.audiorecordtrack;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class DecodeMp3ToPCM {

    /**
     * 方法描述: 将参数一进行解码，然后将结果存储在参数二
     */
    public static boolean decodeMusicFile(String musicFileUrl, String decodeFileUrl) {

        // 解码核心三大类 MediaExtractor是自己创建的， MediaFormat和MediaCodec是通过后续方法获得实例
        MediaExtractor mediaExtractor = new MediaExtractor();
        MediaFormat mediaFormat;
        MediaCodec mediaCodec;

        // 将要解析的音频MP3设置进MediaExtractor实例中
        try {
            mediaExtractor.setDataSource(musicFileUrl);
        } catch (Exception e) {
            System.out.println("设置解码音频文件路径错误");
            return false;
        }

        // 获取关于音频的MediaFormat实例 这个对象中包含了MP3的声道采样频率比特率等信息
        mediaFormat = mediaExtractor.getTrackFormat(0);

        // 采样频率
        int sampleRate = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        // 声道
        int channelCount = mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        // 音长
        long duration = mediaFormat.getLong(MediaFormat.KEY_DURATION);
        // 描述media格式mime类型的关键字
        String mime = mediaFormat.getString(MediaFormat.KEY_MIME);

        System.out.println(sampleRate + "-" + channelCount + "-" + duration + "-" + mime);

        try {
            // 根据音频格式来创建MediaCodec实例对象
            mediaCodec = MediaCodec.createDecoderByType(mime);
            // 用MediaFormat配置MediaCodec
            mediaCodec.configure(mediaFormat, null, null, 0);
        } catch (Exception e) {
            System.out.println("解码器configure出错");
            return false;
        }

        getDecodeData(mediaExtractor, mediaCodec, decodeFileUrl);
        return true;
    }

    private static void getDecodeData(MediaExtractor mediaExtractor, MediaCodec mediaCodec, String decodeFileUrl) {
        // 解码读取MP3文件数据是否结束
        boolean decodeInputEnd = false;
        // 解码输出写如文件是否结束
        boolean decodeOutputEnd = false;

        int sampleDataSize;
        int inputBufferIndex;
        int outputBufferIndex;

        // 以微妙的类型记录从MP3中读取的数据在整个音乐中占多少时间
        long presentationTimeUs = 0;

        final long timeOutUs = 100;

        ByteBuffer sourceBuffer;
        ByteBuffer targetBuffer;

        // 在configure(MediaFormat、Surface、MediaCrypto、int)之后调用它，以获得最初为编解码器配置的输出格式。这样做是为了确定编解码器支持哪些可选配置参数。
//        MediaFormat outputFormat = mediaCodec.getOutputFormat();

        // 开始解码
        mediaCodec.start();

        // 返回一个已清除的、可写的ByteBuffer对象，用于一个dequeued 输入缓冲区索引，以包含输入数据。
        ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
        // 返回一个只读的ByteBuffer，用于一个队列的输出缓冲区索引。返回的缓冲区的位置和限制被设置为有效的输出数据。
        ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();

        // 对readSampleData(ByteBuffer、int)、getSampleTrackIndex()和getSampleTime()的后续调用只检索选定的曲目子集的信息。多次选择相同的轨迹没有效果，轨迹只选择一次。
        mediaExtractor.selectTrack(0);

        // 包含有输出缓存区的偏移量和有效范围大小
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

        // 获得一个带缓存的输出流，将解码后的数据写倒decodeFileUrl中
        BufferedOutputStream bufferedOutputStream = getBufferedOutputStreamFromFile(decodeFileUrl);

        while (!decodeOutputEnd) {
            if (decodeInputEnd) {
                return;
            }

            // -------------------------------------------------从MP3中读取数据-------------------------------------------------------

            try {
                // 从读取缓存区队列中获取一个可用的空ByteBuffer的索引 参数为获取时设置的超时时间
                inputBufferIndex = mediaCodec.dequeueInputBuffer(timeOutUs);

                // 判断是否有可用读取缓存区的索引
                if (inputBufferIndex >= 0) {
                    // 使用索引从inputBuffers中获取对应的ByteBuffer的实例对象的地址值指向给sourceBuffer
                    sourceBuffer = inputBuffers[inputBufferIndex];

                    // 根据sourceBuffer的情况来决定mediaExtractor从MP3文件中读取多少的数据缓存到sourceBuffer中
                    // 返回读取数据的大小
                    sampleDataSize = mediaExtractor.readSampleData(sourceBuffer, 0);

                    // 根据读取的数据大小来判断是否读取完成
                    if (sampleDataSize < 0) {
                        // MP3中的数据已经读取完毕
                        decodeInputEnd = true;
                        sampleDataSize = 0;
                    } else {
                        // 读取的数据样本所对应的音乐时间
                        presentationTimeUs = mediaExtractor.getSampleTime();
                    }

                    // BUFFER_FLAG_END_OF_STREAM 标志流的结束
                    // 根据准备读取的数据大小和对应的音乐时间，将数据缓存倒参数一索引所代表的ByteBuffer中
                    mediaCodec.queueInputBuffer(inputBufferIndex, 0, sampleDataSize, presentationTimeUs, decodeInputEnd ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);

                    if (!decodeInputEnd) {
                        // 跳到下一段需要解码的样本数据
                        mediaExtractor.advance();
                    }
                } else {
                    System.out.println("inputBufferIndex: " + inputBufferIndex);
                }

                // --------------------------------------------------向sourceByteArray中填充读取的MP3数据------------------------------------------------------

                // 从输写缓存区队列中获取一个可用的已成功解码的输出缓冲区的索引
                // 参数一将会填充缓冲区元数据。(就时将缓存区的数据偏移量和有效范围存储进参数一的对象中)
                outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, timeOutUs);

                if (outputBufferIndex < 0) {
                    // 获取索引失败
                    switch (outputBufferIndex) {
                        case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                            // 重新获取输出缓存区队列
                            outputBuffers = mediaCodec.getOutputBuffers();
                            System.out.println();
                            break;
                        case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                            // 输出格式已经更改，后续的数据将遵循新的格式
                            System.out.println();
//                            outputFormat = mediaCodec.getOutputFormat();
//                            sampleRate = outputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE) ?
//                                    outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) :
//                                    sampleRate;
//                            channelCount = outputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ?
//                                    outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) :
//                                    channelCount;
//                            byteNumber = (outputFormat.containsKey("bit-width") ? outputFormat
//                                    .getInteger
//                                            ("bit-width") : 0) / 8;
                            break;
                        default:
                            System.out.println();
                            break;
                    }
                    continue;
                }

                // 根据索引获取存储有MP3缓存数据的ByteBuffer对象
                targetBuffer = outputBuffers[outputBufferIndex];

                // bufferInfo.size代表了targetBuffer中有效的数据量
                byte[] sourceByteArray = new byte[bufferInfo.size];

                // 将targetBuffer中的数据传到sourceByteArray中
                targetBuffer.get(sourceByteArray);
                // 清空数据，下一个循环接着用
                targetBuffer.clear();

                // 将参数一指定的输出缓存区进行释放，让下一个循环有可能可以使用
                mediaCodec.releaseOutputBuffer(outputBufferIndex, false);

                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    // 输出结束了
                    decodeOutputEnd = true;
                }

                // --------------------------------------------------------------------------------------------------------


                if (sourceByteArray.length > 0 && bufferedOutputStream != null) {
                    // sourceByteArray中有数据，所以要进行输出流写入
                    try {
                        bufferedOutputStream.write(sourceByteArray);
                    } catch (Exception e) {
                        System.out.println("输出解压音频数据异常");
                    }
                }
            } catch (Exception e) {
                System.out.println("getDecodeData异常");
            }
        }

        if (bufferedOutputStream != null) {
            try {
                bufferedOutputStream.close();
            } catch (IOException e) {
                System.out.println("关闭bufferedOutputStream异常");
            }
        }
        if (mediaCodec != null) {
            mediaCodec.stop();
            mediaCodec.release();
        }

        if (mediaExtractor != null) {
            mediaExtractor.release();
        }
    }


    private static BufferedOutputStream getBufferedOutputStreamFromFile(String fileUrl) {
        BufferedOutputStream bufferedOutputStream = null;

        try {
            File file = new File(fileUrl);

            if (file.exists()) {
                file.delete();
            }

            file.createNewFile();

            bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(file));
        } catch (Exception e) {
            System.out.println("GetBufferedOutputStreamFromFile异常");
        }

        return bufferedOutputStream;
    }
}
