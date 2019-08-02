## 文档说明：

​		本文档的是adam权限控制后台实现的设计文档，通过阅读本文档，能让读者对该功能的设计思路有一个大致了解，对于想深入了解源代码实现的读者，阅读本文档之后，能更有效率地阅读源代码；如果想快速了解该功能提供了哪些接口，可以参考《adam权限控制接口文档》；

##需求：

[http://wiki.vipkid.com.cn/pages/viewpage.action?pageId=62927508](http://wiki.vipkid.com.cn/pages/viewpage.action?pageId=62927508)

权限控制对资源的操作：

+ get不进行权限校验
+ 有module资源的权限，可以对该module下的资源进行save/update/delete；
+ 没有module资源权限，有resourceType资源的权限，不可以对其他resourceType资源进行操作，可以对该resourceType下的resourceId进行update/delete，也可以save；
+ 没有module、resourceType资源权限，有resourceId权限；不可以对该module/resourceType进行save操作；可以对有权限的resourceId进行update/delete；
+ 对thumb, fm module要放行，以后还会有这种module，所以要把这个放行的module配置放出来；

对权限的修改：

+ Module、resourceType、resourceId都有自己的owner，标记是谁创建的；
+ module、resourceType、resourceId的新增权限：只要操作人有新增权限，就可以把新增权限赋予其他人；
+ module、resourceType、resourceId的删除权限：删除某人module、resourceType、resourceId的权限，操作人必须是该module的owner，也就是说，只有module的owner有该module下面资源的删除权限；其实没有删除操作，只有禁用，一旦禁用deleted=1，则停止后面的权限判断，直接返回无权限；
+ B可以在A的module下面新建resourceType，但是B被禁用访问module之后，就不能访问到该resourceType了，即使是自己创建的；
+ module黑名单功能，其实就是往权限表中直接插入一条在某个module下deleted=1的数据，让该人员没有访问该module的权限；
+ 添加一个特殊模块功能，被标记为特殊模块的模块，不进行权限控制，并且创建resourceId的时候，自动创建resource_type和module；而且提供一个直接从module列出resourceId的查询功能；

## 设计方案：

​		权限实现：每个请求的url中都会带有module, resource_type, resource_id三个参数中任意数量的参数，通过添加注解在需要进行权限控制的接口，并且在springMVC的拦截器中对加了注解的接口进行拦截，获取url中的这三个参数的值，进而对操作人员进行权限判断；

### 一、数据库表设计：

####1、资源表：

```sql
CREATE TABLE `adam_resource` (
	`id` int(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '自增主键',
	`type` int unsigned NOT NULL COMMENT '资源类型，1-module, 2-resourceType, 3-resourceId',
	`name` varchar(64) NOT NULL COMMENT '资源名称，创建之后不可以更改',
	`chinese_name` varchar(64) COMMENT '资源中文名称，创建之后可以修改，无修改限制次数',
	`parent_id` int(10) NOT NULL COMMENT 'resourceId的父id是resourceType，resourceType的父id是module，module的父id为-1',
	`description` varchar(128) COMMENT '对资源的描述',
	`deleted` int NOT NULL COMMENT '删除标记，0-未删除，1-已删除',
  `owner_id` int(10) unsigned NOT NULL COMMENT '资源所属人，对应account_id',
  `owner_email` varchar(64) NOT NULL COMMENT '资源所属人的email',
  `update_person` varchar(64) DEFAULT "" COMMENT '最近更新数据的人',
  `special` int(10) NOT NULL COMMENT '是否是特殊模块，0-不是，1-是',
	`create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
	`update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
	PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='adam权限资源表';
```

#### 2、权限表：

```sql
CREATE TABLE `adam_authorization`(
	`id` int(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '自增主键id',
	`account_id` int(10) unsigned NOT NULL COMMENT 'account表主键id',
	`adam_resource_id` int(10) unsigned NOT NULL COMMENT 'adam权限资源表主键id',
	`deleted` int DEFAULT 1 COMMENT '删除标记，0-未删除，1-已删除',
	`create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
	`update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
	PRIMARY KEY (`id`),
  KEY `idx_account_id` (`account_id`),
  KEY `idx_adam_resource_id` (`adam_resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='adam权限控制表';
```

##### 3、在adam表中添加一个索引：

```sql
ALTER TABLE beexiao.adam
DROP INDEX user_key, 
ADD CONSTRAINT uk_user_key UNIQUE (user_key);
```

### 二、服务器代码逻辑设计

####1、权限拦截功能的实现

​		添加一个用于权限管理的注解@AdamAuthorizationControl，添加一个SpringMVC的拦截器AdamAuthorizationInterceptor，在preHandle()方法中，判断请求的HandleMethod是否包含有@AdamAuthorizationControl注解，有的话，就需要进入权限判断的逻辑；没有的话，就返回true，不进行拦截；权限控制的逻辑，Save和update/delete是不一样的，这里需要进行区分：

通用逻辑：从请求中根据token拿到当前用户的account_id, 接着拿到module, resourceType, resourceId这3个参数；

##### update/delete，对应一个UpdateOrDeleteAuthorizationStrategy，继承自AuthorizationStragy这里使用策略设计模式、混合观察者设计模式，在这些strategy初始化的时候，就注册到SpringMVC那个拦截器上面去，在SpringMVC这个拦截器上面准备一个HashMap，用来保存这些策略，然后通过注解上带有的值，从而从这个HashMap中选择对应的strategy来处理；

1. 先用module去资源表adam_resource查询，拿到它的id，要加上deleted条件

   + 如果不存或者已删除，则告知该module不存在，结束权限判断；
   + 先判断当前account_id是不是就是owner_id，是的话，有权限；

   + 根据module的id去权限表adam_authorization查询，获取到所有有权限访问这个module的account_id，判断当前用户的account_id是不是在这个查询到的account_id列表中，如果是，就说明有当前module的权限，可以进行update/delete的操作;

   + 如果没有，就说明没有module权限，要继续判断resourceType权限；

2. 根据第1步获取到的module的id以及resourceType去查询资源表adam_resource，拿到唯一的以这个module的id作为parent_id、名称为该resourceType的resourceType，要加上deleted条件：

   + 如果不存在或者已删除，则告知该resourceType不存在，结束权限判断；
   + 先判断当前account_id是不是就是owner_id，是的话，有权限；

   + 根据resourceType的id去权限表adam_authorization查询，获取到所有有权限访问这个module/resourceType的account_id，然后判断当前用户的account_id在不在里面，如果在，就说明有该module/resourceType的权限，可以进行update/delete操作；

   + 否则没有该module/resourceType权限，要继续判断module/resourceType/resourceId权限；

3. 根据第2步的resourceType的id以及resourceId去资源表adam_resource查询，拿到唯一的以这个resourceType的id作为parent_id、名称为该resourceId的resourceId，要加上deleted条件：

   + 如果不存在或者已删除，则返回告知该resourceId不存在，结束权限判断；
   + 先判断当前account_id是不是就是owner_id，是的话，有权限；

   + 根据resourceId的id，去权限表adam_authorization进行查询，获取到所有有权限访问这个module/resourceType/resourceId的account_id，然后判断当前用户的account_id是不是在里面，在的话，有该module/resourceType/resourceId的权限，可以进行update/delete操作；

   + 不在的话，没权限；

#####save，对应一个策略SaveAuthorizationStrategy，内部逻辑跟update/delete基本上不一样：

1. 先用module去资源表adam_resource查询，拿到它的id，要加上deleted条件

   + 如果不存或者已删除，则说明是新建module，有权限；
   + 先判断当前account_id是不是就是owner_id，是的话，有权限；

   + 根据module的id去权限表adam_authorization查询，获取到所有有权限访问这个module的account_id，判断当前用户的account_id是不是在这个查询到的account_id列表中，如果是，就说明有当前module的权限，可以进行save的操作;

   + 如果没有，就说明没有module权限，要继续判断resourceType权限；

2. 根据第1步获取到的module的id以及resourceType去查询资源表adam_resource，拿到唯一的以这个module的id作为parent_id、名称为该resourceType的resourceType，要加上deleted条件：

   + 如果不存在或者已删除，则说明是在该module下新建resourceType，因为1判断没有module权限，所以无权限在该module下新建resourceType；
   + 先判断当前account_id是不是就是owner_id，是的话，有权限；

   + 根据resourceType的id去权限表adam_authorization查询，获取到所有有权限访问这个module/resourceType的account_id，然后判断当前用户的account_id在不在里面，如果在，还需要进一步判断：

   ​		1>.根据当前resourceType的id以及resourceId去查询，如果已经存在该resourceId，就告知已存在该module/resrouceType/resourceId，不能进行save操作，请走update接口，然后结束权限判断；

   ​		2>. 如果不存在，说明是save操作，有权限；

   + 如果不在，说明没有该module/resourceType的权限，不能进行save操作；

#### 2、对权限进行操作的接口

 1. 返回所有module的接口；(顺便要返回该操作人是否有除了删除权限的权限(来自adam_authorization表)、以及删除权限的权限(owner_id))

 2. 根据module查询resourceType的接口；(顺便要返回该操作人是否有除了删除权限的权限(来自adam_authorization表)、以及删除权限的权限(owner_id))

 3. 根据module/resourceType查询所有resourceId的接口；(顺便要返回该操作人是否有除了删除权限的权限(来自adam_authorization表)、以及删除权限的权限(owner_id))

 4. 获取拥有该资源权限的所有人，且仅返回该资源的，比如resourceId就只返回resourceId的权限，不包含resourceType、module的权限；

 5. (添加module、resourceType的save,delete, update接口，update只能修改module的chinese_name、description；

    delete要判断，如果有叶子节点，就不能删除；save接口也要判断，如果之前有这条记录，只不过是软删除了，那么save就是把deleted改为0)；

 6. 对权限进行修改的接口(这个接口要加@AdamAuthrizationControl注解)：

    1. 接收module, resourceType, resourceId, List<Integer> accountIds，boolean add五个参数；
    2. 先判断resourceId是不是null，如果不是，说明是给accuntIds添加/移出resourceId级别的权限：
       1. 先判断module和resourceType都不为null，如果有一个为null，告知参数错误；
       2. 根据module拿到它的id，根据module的id以及resourceType拿到这个resourceType的id，根据这个resourceTyp的id以及resourceId拿到resourceId的id；
       3. 判断add是true还是false，如果是true，那就是添加权限直接把这些accountIds跟resourceId的id配对，批量写入Adam_authorization表；(这里不能批量写入，每个人都要进行判断是不是之前被禁用过，如果是禁用，就是再次开启权限，需要进行更新操作，所以每个人都要单独进行判断)
       4. 如果是false，就是移出她们在该module下面的resourceId的权限，那就要看当前操作人是不是该module的owner，只有module的owner才能进行该Module下面的权限删除操作，不是的话，告知不是module创建人，不能删除权限，是的话，就批量更新adam_authorization表中这些accountIds跟该resourceId的id配对的数据的deleted为1。

    3. 如果resourceId为null，接着判断resourceType是不是null，如果不是，说明是给accounts添加/移出resourceType级别的权限：
       1. 先判断module是不是null，如果是，告知参数错误；
       2. 根据module拿到它的id，根据module的id以及resourceType拿到这个resourceType的id；
       3. 判断add是true还是false，如果是true，那就是添加权限直接把这些accountIds跟resourceType的id配对，批量写入Adam_authorization表；(这里不能批量写入，每个人都要进行判断是不是之前被禁用过，如果是禁用，就是再次开启权限，需要进行更新操作，所以每个人都要单独进行判断)
       4. 如果是false，就是移出她们在该module下面的resourceType的权限，那就要看当前操作人是不是该module的owner，只有module的owner才能进行该Module下面的权限删除操作，不是的话，告知不是module创建人，不能删除权限，是的话，就批量更新adam_authorization表中这些accountIds跟该resourceType的id配对的数据deleted为1。

    4. 如果resourceType为null，接着判断module是不是null，如果是，告知参数错误，如果不是，说明是给accounts添加/移出module级别的权限：
       1. 根据module拿到它的id；
       2. 判断add是true还是false，如果是true，那就是添加权限直接把这些accountIds跟module的id配对，批量写入Adam_authorization表；(这里不能批量写入，每个人都要进行判断是不是之前被禁用过，如果是禁用，就是再次开启权限，需要进行更新操作，所以每个人都要单独进行判断)
       3. 如果是false，就是移出她们在该module下面的权限，那就要看当前操作人是不是该module的owner，只有module的owner才能进行该Module下面的权限删除操作，不是的话，告知不是module创建人，不能删除权限，是的话，就批量更新adam_authorization表中这些accountIds跟该module的id配对的数据deleted为1。

    注：批量插入的功能，mybatis是有的，但是批量更新这个功能，mybatis没有提供，所以需要自己写；

#### 3、对之前已有的接口进行修改

1. 修改save接口，在save到adam的同时，要判断resourceType是不是存在，不存在不可以进行新增操作，先增加resourceType，也要把新建的resourceId保存进Adam_resource表；

   仅仅新增resourceId，添加1条记录到Adam_resource表；

   新增module、resourceType的功能在其他接口，不在这里；

2. 修改delete接口，在delete掉module/resourceType/resourceId的同时，也要delete掉Adam_resource表中的这条记录；

   删除module, resourceType的操作不在这里，在其他接口；

3. 修改update接口，update可以update两张表的数据，并且每次update, Adam_resource表中的update_person字段都要更新；

4. 修改get接口，数据来源于2张表，不再仅仅是一张表了；

5. (黑名单的添加、删除、查看、修改)

6. 写一个功能，给目前已有的Adam中的数据，加上权限；

### 三、本地测试

#### 1、测试资源save

1. 测试新建一个module, 新建一个resourceType, 新建一个resourceId，更新一个resourceId；
2. 在没module权限的module下新建，在没有resourceType权限下新建；

#### 2、测试资源update

​		更新一条有权限、无权限的的已有的adam记录，更新一条不存在的adam记录；

#### 3、测试资源delete

​		删除一条有权限的、无权限的已有的记录，删除一条不存在的记录；

#### 4、测试资源get

​		随便测，我们的代码没有影响get查询；

#### 5、对权限的添加、删除测试

		1. 我是module owner，对该module下面的resourceType, resourceId资源添加、删除accountIds测试；
  		2. 我不是该module owner，但是有权限，测试添加resourceType, resourceId资源； 测试删除(没权限)；
  		3. 我没有该module权限，给该module添加、删除resourceType, resourceId资源；
  		4. 新建一个module、resourceType、resourceId；



### 四、上线操作

1、首先调用/api/v2/adam/temp/repeat/data，删除重复的user_key；

2、接着执行那3句sql，创建adam_resource, adam_authorization, 添加unique key；

3、调用/api/v2/adam/temp/create/auth，把权限加进Adam_resources表；