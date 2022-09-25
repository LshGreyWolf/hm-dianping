package com.hmdp;

import com.hmdp.service.IShopService;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.hmdp.service.impl.ShopServiceImpl;

@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    private IShopService shopService;



}
