package com.atguigu.product;

import com.alibaba.cloud.nacos.discovery.NacosServiceDiscovery;
import com.alibaba.nacos.api.exception.NacosException;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.util.List;

@SpringBootTest
@RequiredArgsConstructor
@Disabled("需要Nacos服务器环境，仅在本地手动运行")
public class DiscoveryTest {
    private final DiscoveryClient discoveryClient;
    private final NacosServiceDiscovery nacosServiceDiscovery;
    @Test
    void nacosTest() throws NacosException {
        for (String service : nacosServiceDiscovery.getServices()) {
            System.out.println("service: " + service);
            List<ServiceInstance> instances = nacosServiceDiscovery.getInstances(service);
            for (ServiceInstance instance : instances) {
                System.out.println("instance: " + instance);
                System.out.println("ip = " + instance.getHost()+";"+"port = " + instance.getPort());
            }
        }
    }
    @Test
    void discovery() {
        for (String serviceId : discoveryClient.getServices()) {
            System.out.println("service = " + serviceId);
            List<ServiceInstance> instanceList = discoveryClient.getInstances(serviceId);
            instanceList.forEach(serviceInstance -> {
                System.out.println("serviceInstance = " + serviceInstance);
                System.out.println("ip = " + serviceInstance.getHost()+";"+"port = " + serviceInstance.getPort());
            });
        }
    }
}
