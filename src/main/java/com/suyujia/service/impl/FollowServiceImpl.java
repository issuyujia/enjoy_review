package com.suyujia.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.suyujia.dto.Result;
import com.suyujia.dto.UserDTO;
import com.suyujia.entity.Follow;
import com.suyujia.mapper.FollowMapper;
import com.suyujia.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.suyujia.service.IUserService;
import com.suyujia.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author syj
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //redis前缀key
        String key = "follows:";
        //获取登录用户
        Long userId = UserHolder.getUser().getId();
        //1.判断是关注还是取关
        if (isFollow) {
            //关注
            Follow follow = new Follow();
            follow.setFollowUserId(followUserId);
            follow.setUserId(userId);
            follow.setCreateTime(LocalDateTime.now());
            boolean isSuccess = save(follow);
            if (isSuccess) {
                //把关注成员放入redis当中
                stringRedisTemplate.opsForSet().add(key + userId, followUserId.toString());
            }
        } else {
            //取关,delete from tb_follow where userId = ? and follow_user_id = ?;
            boolean isSuccess = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", followUserId));
            if (isSuccess) {
                //把关注成员移除出去
                stringRedisTemplate.opsForSet().remove(key + userId, followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        //1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        //2.查询当前查询的用户是否已经关注了
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        return Result.ok(count > 0);
    }

    /**
     * 查询显示出共同关注用户列表
     *
     * @param id
     * @return
     */
    @Override
    public Result followCommons(Long id) {
        //获取当前的用户id
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        //目标用户
        String key2 = "follows:" + id;
        //查询当前俩个人的共同关注用户
        Set<String> unionSet = stringRedisTemplate.opsForSet().intersect(key, key2);
        if (unionSet == null || unionSet.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        //解析id集合
        List<Long> ids = unionSet.stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());
        //查询用户
        List<UserDTO> userDTOList = userService.listByIds(ids).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOList);
    }
}
