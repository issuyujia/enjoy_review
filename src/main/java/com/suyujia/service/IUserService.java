package com.suyujia.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.suyujia.dto.LoginFormDTO;
import com.suyujia.dto.Result;
import com.suyujia.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author syj
 */
public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);

    Result sign();

    Result signCount();
}
