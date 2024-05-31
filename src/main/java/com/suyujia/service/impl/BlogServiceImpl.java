package com.suyujia.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.suyujia.dto.Result;
import com.suyujia.dto.ScrollResult;
import com.suyujia.dto.UserDTO;
import com.suyujia.entity.Blog;
import com.suyujia.entity.Follow;
import com.suyujia.entity.User;
import com.suyujia.mapper.BlogMapper;
import com.suyujia.service.IBlogService;
import com.suyujia.service.IFollowService;
import com.suyujia.service.IUserService;
import com.suyujia.utils.SystemConstants;
import com.suyujia.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.suyujia.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.suyujia.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author syj
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IBlogService blogService;
    @Resource
    private IUserService userService;
    @Resource
    private IFollowService followService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        // 3.查询blog是否已经被当前用户点赞了
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    private void isBlogLiked(Blog blog) {
        //1.判断当前登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            //用户未登录，无需查询登录信息
            return;
        }
//        Long userId = UserHolder.getUser().getId();
        Long userId = user.getId();
        //2.判断当前用户是否已经点赞
        String key = "blog:liked:" + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    @Override
    public Result queryBlogById(Long id) {
        // 1.查询blog
        Blog blog = blogService.getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在！！！");
        }
        // 2，查询blog相关的用户
        queryBlogUser(blog);
        // 3.查询blog是否已经被当前用户点赞了
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
    }

    @Override
    public Result likeBlog(Long id) {
        //1.判断当前登录用户
        Long userId = UserHolder.getUser().getId();
        //2.判断当前用户是否已经点赞
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        //3.如果未点赞，可以点赞
        if (score == null) {
            //3.1. 数据库点赞数＋1
            boolean isSuccess = blogService.update()
                    .setSql("liked = liked + 1").eq("id", id).update();
            if (isSuccess) {
                //3.2. 保存用户到redis的set集合 zadd key value score
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }

        } else {
            //4.1 数据库点赞数-1
            boolean isSuccess = blogService.update()
                    .setSql("liked = liked - 1").eq("id", id).update();
            //4.2 把用户从redis的set集合中移除
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        //1.查询top的点赞用户 zrange key 0 4
        String key = BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        //2.解析出其中的用户id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        //3.根据用户id查询用户
        List<UserDTO> userDTOS = userService.query().in("id", ids)
                .last("OEDER BY FIELD(id," + idStr + ")")
                .list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        //4.返回
        return Result.ok(userDTOS);
    }

    /**
     * 保存博客信息
     *
     * @param blog
     * @return
     */
    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSuccess = blogService.save(blog);
        if (!isSuccess) {
            return Result.fail("新增笔记失败");
        }
        // 查询笔记作者的全部粉丝 select * from tb_follow where follow_user_id=?
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        // 推送笔记id给所有的粉丝
        for (Follow follow : follows) {
            //获取粉丝id
            Long userId = follow.getUserId();
            //推送
            String key = FEED_KEY + userId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //1.查询当前用户
        Long userId = UserHolder.getUser().getId();
        //2.查询收件箱
        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        //非空判断
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int os = 1;
        //3.解析数据：blogId.minTime(时间戳)、offset(偏移量)
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            //获取id
            String idStr = tuple.getValue();
            ids.add(Long.valueOf(idStr));
            //获取分数（时间戳）
            long time = tuple.getScore().longValue();
            if (time == minTime) {
                os++;
            } else {
                minTime = time;
                os = 1;
            }
        }
        //4.根据id查询blog
        String idStr = StrUtil.join(",",ids);
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for(Blog blog : blogs){
            //查询blog相关的用户
            queryBlogUser(blog);
            //查询blog是否点赞
            isBlogLiked(blog);
        }
        //5.封装并返回
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(os);
        scrollResult.setMinTime(minTime);
        return Result.ok(scrollResult);
    }
}
