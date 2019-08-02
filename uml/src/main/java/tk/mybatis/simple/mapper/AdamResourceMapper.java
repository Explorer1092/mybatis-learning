package tk.mybatis.simple.mapper;

import tk.mybatis.simple.model.AdamResource;

import java.util.List;

/**
 * @author hujun
 * @email hujun1@vipkid.com.cn
 * @date 2019-07-30 09:46
 */
public interface AdamResourceMapper {

    List<AdamResource> selectAll();

    List<AdamResource> selectByAdamResource(AdamResource adamResource);

}
