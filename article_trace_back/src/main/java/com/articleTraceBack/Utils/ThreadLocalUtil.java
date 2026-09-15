package com.articleTraceBack.Utils;

@SuppressWarnings("all")
public class ThreadLocalUtil {
    //为每个线程保存一份独立变量；本类用于在请求线程内传递登录用户信息。
    private static final ThreadLocal THREAD_LOCAL = new ThreadLocal();

    //取出当前线程保存的值
    public static <T> T get(){
        return (T) THREAD_LOCAL.get();
    }
	
    //把值保存到当前线程
    public static void set(Object value){
        THREAD_LOCAL.set(value);
    }


    //清除ThreadLocal
    public static void remove(){
        THREAD_LOCAL.remove();
    }
}
