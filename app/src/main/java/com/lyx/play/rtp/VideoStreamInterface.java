package com.lyx.play.rtp;

public interface VideoStreamInterface {
    void onVideoStream(byte[] var1);
    void releaseResource();
}