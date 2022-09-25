package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexPatterns;
import com.hmdp.utils.RegexUtils;
import com.sun.javafx.tk.TKClipboard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.TimeoutUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private IUserService userService;


    @Override
    public Result sendCode(String phone, HttpSession session) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式有误，请重新输入!");
        }
        String code = RandomUtil.randomNumbers(4);
        redisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
//        session.setAttribute("code", code);
        log.info("验证码，{}", code);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {

        String code = loginForm.getCode();
        //  从redis中获取验证码并校验
        String cacheCode = redisTemplate.opsForValue().get(LOGIN_CODE_KEY + loginForm.getPhone());

//        Object sessionCode = session.getAttribute("code");
        if (code.equals(cacheCode) && RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            return Result.fail("验证码错误或者手机格式错误！");
        }
        LambdaQueryWrapper<User> lambdaQueryWrapper = new LambdaQueryWrapper<User>()
                .eq(User::getPhone, loginForm.getPhone());
        User user = userService.getOne(lambdaQueryWrapper);
        if (user == null) {
            user = new User();
            user.setPhone(loginForm.getPhone());
            user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(4));
            userService.save(user);
        }
        //登录成功


        //将用户信息存到redis中，token作为key
        //随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString(true);
        //将user对象转为hashMap，作为value
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user, userDTO);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        String tokenKey = LOGIN_USER_KEY + token;
        //存储
        redisTemplate.opsForHash().putAll(tokenKey, userMap);
        //设置有效期 session 的30分钟有效期是30分钟不访问session才失效。但是这里明显是从用户登陆后三十分钟后失效，不可以这样
//        redisTemplate.expire(LOGIN_USER_KEY+token,30,TimeUnit.MINUTES);
        //使用登录拦截器来完成这个操作，用户一被拦截，便更新token的有效期
        //返回token
//        session.setAttribute("user", userDTO);
        return Result.ok(tokenKey);
    }
}
