package com.lyx.play;

import java.util.LinkedList;

/**
 * BufferQueue
 * method {add() pop()} 加了同步机制，线程安全
 * <p>
 * author:  luoyingxing
 * date: 2018/6/15.
 */
public class BufferQueue<T> {
    /**
     * 通过链表来缓存数据，便于存取
     */
    private LinkedList<T> list = new LinkedList<>();

    /**
     * 清空数据
     */
    public void clear() {
        list.clear();
    }

    /**
     * 判断当前队列是否为空
     *
     * @return boolean
     */
    public boolean isEmpty() {
        return list.isEmpty();
    }

    /**
     * 获取当前队列的长度
     *
     * @return int
     */
    public int size() {
        return list.size();
    }

    /**
     * 入队列
     *
     * @param obj T
     */
    public synchronized void add(T obj) {
        list.add(obj);
    }

    /**
     * 出队列
     *
     * @return T
     */
    public synchronized T pop() {
        if (isEmpty()) {
            return null;
        }

        return list.poll();
    }

    @Override
    public String toString() {
        return "BufferQueue：" + list;
    }
}