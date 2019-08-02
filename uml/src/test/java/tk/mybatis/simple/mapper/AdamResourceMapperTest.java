package tk.mybatis.simple.mapper;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.BeforeClass;
import org.junit.Test;
import tk.mybatis.simple.model.AdamResource;

import java.io.IOException;
import java.io.Reader;
import java.util.List;

/**
 * @author hujun
 * @email hujun1@vipkid.com.cn
 * @date 2019-07-30 09:52
 */
public class AdamResourceMapperTest {

    private static SqlSessionFactory sqlSessionFactory;

    @BeforeClass
    public static void init(){
        try {
            Reader reader = Resources.getResourceAsReader("mybatis-config.xml");
            sqlSessionFactory = new SqlSessionFactoryBuilder().build(reader);
            reader.close();
        } catch (IOException ignore) {
            ignore.printStackTrace();
        }
    }

    @Test
    public void testSelectAll(){
        SqlSession sqlSession = sqlSessionFactory.openSession();
        try {
            AdamResourceMapper mapper = sqlSession.getMapper(AdamResourceMapper.class);
            List<AdamResource> adamResources = mapper.selectAll();
            printCountryList(adamResources);
            AdamResource adamResource = new AdamResource();
            adamResource.setName("mgt");
            List<AdamResource> countryList = mapper.selectByAdamResource(adamResource);
            printCountryList(countryList);
        } finally {
            sqlSession.close();
        }
    }

    private void printCountryList(List<AdamResource> countryList){
        for(AdamResource adamResource : countryList){
            System.out.println(adamResource);
        }
    }

}
