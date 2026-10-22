package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IShopService shopService;


    @Test
    void loadShopData() {
        //1、查询店铺信息
        List<Shop> list = shopService.list();
        //2、把店铺分组，按照typeid分组，id一致的放到一个集合
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        //3、将分类信息和店铺信息写入Redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()){
            //3.1、获取分类id
            long typeId = entry.getKey();
            String key = "shop:geo:" + typeId;
            //3.2、获取分类下的店铺列表
            List<Shop> shops = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>();
            //3.3、将店铺列表写入Redis GEOADD key 经度 维度 member
            for (Shop shop : shops) {
                //一个个加
//                stringRedisTemplate.opsForGeo().add(key, new RedisGeoCommands.GeoLocation<>(shop.getId().toString(), new Point(shop.getX(), shop.getY())));
                locations.add(new RedisGeoCommands.GeoLocation<>(shop.getId().toString(), new Point(shop.getX(), shop.getY())));
            }
//            批量加
            stringRedisTemplate.opsForGeo().add(key,locations);
        }
    }


}
