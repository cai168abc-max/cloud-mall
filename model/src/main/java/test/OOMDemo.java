package test;
import  java.util.List;
import java.util.ArrayList;

public class OOMDemo {
    private static final List<byte[]> LEAK_LIST = new ArrayList<>();
    public static void main(String[] args )throws InterruptedException {
        System.out.println("OOM演示程序启动，开始循环创建大对象...");
        while(true){
            byte[] bigObject = new byte[1024 * 1024];
            LEAK_LIST.add(bigObject);
            Thread.sleep(100);
        }
    }
}
