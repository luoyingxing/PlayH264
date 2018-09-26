package com.lyx.play.activity;

import android.app.Activity;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.lyx.play.BufferQueue;
import com.lyx.play.R;


import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * author:  luoyingxing
 * date: 2018/9/26.
 */
public class PlayActivity extends Activity implements SurfaceHolder.Callback {
    private SurfaceView surfaceView;

    private MediaCodec mediaCodec;

    private String TYPE = "video/avc";
    private int videoWidth = 1920;
    private int videoHeight = 1080;

    private int FrameRate = 15;

    private ExecutorService mCachedThreadPool;

    private boolean stop;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play);

        surfaceView = findViewById(R.id.surface);
        surfaceView.getHolder().addCallback(this);

        if (null == mCachedThreadPool) {
            mCachedThreadPool = Executors.newCachedThreadPool();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            mediaCodec = MediaCodec.createDecoderByType(TYPE); //通过多媒体格式名创建一个可用的解码器
        } catch (IOException e) {
            e.printStackTrace();
        }

        MediaFormat format = MediaFormat.createVideoFormat(TYPE, videoWidth, videoHeight);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FrameRate);  //设置帧率
        mediaCodec.configure(format, holder.getSurface(), null, 0);

        play();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mediaCodec.stop();
        mediaCodec.release();
    }

    private void play() {
        mediaCodec.start();
        mCachedThreadPool.execute(new PlayRunnable());
    }

    private String url = "http://192.168.0.224:2400/api/get-video-h264";

    private ByteBuffer buffer;
    private BufferQueue<byte[]> bufferQueue;
    private int mRealCompareIndex;

    private class PlayRunnable implements Runnable {

        @Override
        public void run() {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(20 * 1000);
                conn.setReadTimeout(20 * 1000);
                conn.setRequestProperty("Connection", "Keep-Alive");
                conn.setRequestProperty("User-Agent", "CONWIN");
                conn.setRequestMethod("GET");
                conn.connect();

                InputStream inputStream = conn.getInputStream();

                if (HttpURLConnection.HTTP_OK == conn.getResponseCode()) {
                    analyzeHeaders(conn.getHeaderFields());

                    initSplit();

                    buffer = ByteBuffer.allocate(1024 * 80 * 2);
                    bufferQueue = new BufferQueue<>();

                    inputBuffers = mediaCodec.getInputBuffers();

                    while (!stop) {
                        int len = inputStream.available();

                        if (len != -1) {
                            byte[] read = new byte[len];

                            int readLen = inputStream.read(read);

                            if (0 < readLen) {
//                                Log.d("====== ", new String(read));

                                bufferQueue.add(read);
                                //直接写入数据
                                buffer.put(read);

                                if (buffer.position() > regexLen) { // buffer的长度必须大于分隔符才有比较的意义

                                    //记录当前填充数据后的Buffer读写的位置
                                    int position = buffer.position();

                                    //找到块数据的分隔符的起始下标
                                    int findIndex = -1;

                                    int j; //主串的开始查找位置，取决于上一次的结果
                                    j = mRealCompareIndex;

                                    int s = 0; //匹配串初始位置
                                    //KMP查找分隔符，分离块数据
                                    while (j < position) {
                                        if (s == -1 || buffer.get(j) == regexBytes[s]) {  //比较字符是否相等
                                            j++;
                                            s++;
                                            if (s >= regexLen) {
                                                //模式串被完全匹配
                                                findIndex = j - regexLen;
                                                break;
                                            }
                                        } else {
                                            s = regexNext[s];  //不等，主串j不变，模式串s变
                                        }
                                    }

                                    if (findIndex != -1) {
                                        //取出块数据

                                        if (findIndex >= mSplitLen) { //若小于，没法比较，因为块数据不能比分隔符的数据小，否则没意义
                                            int jj = 0; //主串初始位置
                                            int ss = 0; //匹配串初始位置

                                            while (jj < findIndex) {
                                                if (ss == -1 || buffer.get(jj) == mSplitBytes[ss]) { //比较字符是否相等
                                                    jj++;
                                                    ss++;
                                                    if (ss >= mSplitLen) {
                                                        //模式串被完全匹配
                                                        int n = jj - mSplitLen;

                                                        byte[] header = new byte[n];
                                                        buffer.position(0);
                                                        buffer.mark();
                                                        buffer.get(header, 0, header.length);

                                                        byte[] image = new byte[findIndex - n - mSplitLen];
                                                        buffer.position(n + mSplitLen);
                                                        buffer.mark();
                                                        buffer.get(image, 0, image.length);

                                                        dispatchBuffer(header, image);
                                                        break;
                                                    }
                                                } else {
                                                    ss = mSplitNext[ss];  //不等，主串j不变，模式串s变
                                                }
                                            }
                                        }

                                        //重置
                                        mRealCompareIndex = 0;

                                        resetBuffer(buffer, position, findIndex, regexLen);
                                    } else {
                                        //若是没有找到，则将比较的小标重置到当前buffer长度 - 分隔符长度
                                        mRealCompareIndex = position - regexLen - 1;
                                    }
                                }

                            }


                            if (-1 == readLen) {
                                stop = true;
                            }
                        }
                    }
                }

                inputStream.close();
                conn.disconnect();
            } catch (ProtocolException e) {
                e.printStackTrace();
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void dispatchBuffer(byte[] header, byte[] image) {
        Log.d("====== ", new String(header));
        decodeH264(image);
    }

    private ByteBuffer[] inputBuffers;

    private void decodeH264(byte[] image) {
        int inIndex = mediaCodec.dequeueInputBuffer(10 * 1000);

        if (inIndex >= 0) {
            ByteBuffer byteBuffer = inputBuffers[inIndex];
            byteBuffer.clear();
            byteBuffer.put(image, 0, image.length);
            //在给指定Index的inputbuffer[]填充数据后，调用这个函数把数据传给解码器
            mediaCodec.queueInputBuffer(inIndex, 0, image.length, 0, 0);
        }

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long startMs = System.currentTimeMillis();
        int outIndex = mediaCodec.dequeueOutputBuffer(info, 10 * 1000);
        if (outIndex >= 0) {
//            //帧控制是不在这种情况下工作，因为没有PTS H264是可用的
            while (info.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            boolean doRender = (info.size != 0);
            //对outputbuffer的处理完后，调用这个函数把buffer重新返回给codec类。
            mediaCodec.releaseOutputBuffer(outIndex, doRender);
        }
    }

    private void resetBuffer(ByteBuffer buffer, int curPosition, int findIndex, int regexLength) {
        byte[] others = new byte[curPosition - findIndex - regexLength];

        buffer.position(findIndex + regexLength);
        buffer.mark();
        buffer.get(others, 0, curPosition - findIndex - regexLength);

        buffer.clear();
        buffer.put(others);
    }

    private void initSplit() {
        String split = "\r\n\r\n";
        mSplitBytes = split.getBytes();
        mSplitLen = mSplitBytes.length;
        mSplitNext = new int[mSplitLen];
        transformKMPNext(mSplitNext, mSplitBytes);
    }

    /**
     * 响应头和图片数据的分隔符
     */
    private byte[] mSplitBytes;

    /**
     * 响应头和图片数据的分隔符的长度
     */
    private int mSplitLen;

    /**
     * 响应头和图片数据的分隔符的KMP next
     */
    private int[] mSplitNext;
    private String mRegex;
    private byte[] regexBytes;
    private int regexLen;
    private int[] regexNext;

    private void analyzeHeaders(Map<String, List<String>> map) {
        if (null == map) {
            return;
        }

        if (TextUtils.isEmpty(mRegex)) {
            findRegex(map);
        }
    }

    private void findRegex(Map<String, List<String>> map) {
        Log.d("PlayActivity", map.toString());
        List<String> list = map.get("content-type");

        if (null != list) {
            for (String str : list) {
                if (str.contains("boundary")) {
                    parseRegex(str);
                    break;
                }
            }
        }
    }

    private void parseRegex(String str) {
        mRegex = str.substring(str.lastIndexOf("boundary") + "boundary".length() + 1, str.length());
        Matcher m = Pattern.compile("[-]6").matcher(mRegex);
        if (!m.find()) {
            mRegex = "--" + mRegex;
        }

        //Initialize the regex length and array next
        regexBytes = mRegex.getBytes();
        regexLen = regexBytes.length;
        regexNext = new int[regexLen];
        transformKMPNext(regexNext, regexBytes);
    }

    private void transformKMPNext(int[] next, byte[] str) {
        next[0] = -1;
        int k = -1;
        int j = 0;
        while (j < str.length - 1) {
            if (k == -1 || str[j] == str[k]) {
                j++;
                k++;
                next[j] = k;
            } else {
                k = next[k];
            }
        }
    }

}