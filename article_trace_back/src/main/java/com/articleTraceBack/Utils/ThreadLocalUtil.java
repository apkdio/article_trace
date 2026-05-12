package com.articleTraceBack.Utils;

@SuppressWarnings("all")
public class ThreadLocalUtil {
    //提供ThreadLocal对象,使不同线程的应用可以共享变量。
    private static final ThreadLocal THREAD_LOCAL = new ThreadLocal();

    //根据键获取值
    public static <T> T get(){
        return (T) THREAD_LOCAL.get();
    }
	
    //存储键值对
    public static void set(Object value){
        THREAD_LOCAL.set(value);
    }


    //清除ThreadLocal
    public static void remove(){
        THREAD_LOCAL.remove();
    }
}
