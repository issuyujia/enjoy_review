package com.suyujia.utils;

import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.判断是否需要拦截（ThreadLocal中是否存在有用户）
        if(UserHolder.getUser()==null){
            response.setStatus(401);
            //拦截
            return false;
        }
        //有用户，则放行

        return true;
    }
}
