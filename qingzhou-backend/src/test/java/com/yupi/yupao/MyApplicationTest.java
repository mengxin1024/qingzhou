package com.yupi.yupao;

import com.yupi.yupao.model.domain.User;
import com.yupi.yupao.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.DigestUtils;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * 测试类
 *
 */
@SpringBootTest
class MyApplicationTest {

    @Autowired
    private UserServiceImpl userService;

//    @Test
//    void testDigest() throws NoSuchAlgorithmException {
//        String newPassword = DigestUtils.md5DigestAsHex(("abcd" + "mypassword").getBytes());
//        System.out.println(newPassword);
//    }

//    @Test
//    void testsearchUsersByTags() {
//        List<String> strings = new ArrayList<>();
//        strings.add("java");
//        strings.add("c++");
//        List<User> users = userService.searchUsersByTags(strings);
//        System.out.println(users);
//    }

}
