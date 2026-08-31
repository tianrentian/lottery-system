-- 设置客户端与服务器之间的字符集为utf8mb4，这个字符集可以存储任何Unicode字符。
SET NAMES utf8mb4;
-- 关闭外键约束检查，这通常在创建或修改表结构时使用，以避免由于外键约束而导致的创建失败。
SET FOREIGN_KEY_CHECKS = 0;

-- 本脚本允许在本地或云端重复执行：只补充缺失的库、表和测试账号，不删除已有数据。
create DATABASE IF NOT EXISTS `lottery_system` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE `lottery_system`;

-- ----------------------------
-- Table structure for activity
-- ----------------------------
create TABLE IF NOT EXISTS `activity`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT comment '主键',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP comment '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON update CURRENT_TIMESTAMP comment '更新时间',
  `activity_name` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL comment '活动名称',
  `description` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL comment '活动描述',
  `status` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL comment '活动状态',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_id`(`id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 24 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci ROW_FORMAT = DYNAMIC;
-- ENGINE = InnoDB：指定表的存储引擎为InnoDB，这是MySQL的默认存储引擎，支持事务、外键等特性。
-- AUTO_INCREMENT = 24：为自动增长的ID字段设置起始值。
-- ROW_FORMAT = DYNAMIC：设置行的存储格式为动态，允许行随着数据的变化而变化。

-- ----------------------------
-- Table structure for activity_prize
-- ----------------------------
create TABLE IF NOT EXISTS `activity_prize`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT comment '主键',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP comment '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON update CURRENT_TIMESTAMP comment '更新时间',
  `activity_id` bigint NOT NULL comment '活动id',
  `prize_id` bigint NOT NULL comment '活动关联的奖品id',
  `prize_amount` bigint NOT NULL DEFAULT 1 comment '关联奖品的数量',
  `prize_tiers` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL comment '奖品等级',
  `status` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL comment '活动奖品状态',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_id`(`id` ASC) USING BTREE,
  UNIQUE INDEX `uk_a_p_id`(`activity_id` ASC, `prize_id` ASC) USING BTREE,
  INDEX `idx_activity_id`(`activity_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 32 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for activity_user
-- ----------------------------
create TABLE IF NOT EXISTS `activity_user`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT comment '主键',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP comment '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON update CURRENT_TIMESTAMP comment '更新时间',
  `activity_id` bigint NOT NULL comment '活动时间',
  `user_id` bigint NOT NULL comment '圈选的用户id',
  `user_name` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL comment '用户名',
  `status` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL comment '用户状态',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_id`(`id` ASC) USING BTREE,
  UNIQUE INDEX `uk_a_u_id`(`activity_id` ASC, `user_id` ASC) USING BTREE,
  INDEX `idx_activity_id`(`activity_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for prize
-- ----------------------------
create TABLE IF NOT EXISTS `prize`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT comment '主键',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP comment '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON update CURRENT_TIMESTAMP comment '更新时间',
  `name` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL comment '奖品名称',
  `description` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL comment '奖品描述',
  `price` decimal(10, 2) NOT NULL comment '奖品价值',
  `image_url` varchar(2048) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL comment '奖品展示图',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_id`(`id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 18 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user
-- ----------------------------
create TABLE IF NOT EXISTS `user`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT comment '主键',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP comment '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON update CURRENT_TIMESTAMP comment '更新时间',
  `user_name` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL comment '用户姓名',
  `email` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL comment '邮箱',
  `phone_number` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL comment '手机号',
  `password` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL comment '登录密码',
  `identity` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL comment '用户身份',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_id`(`id` ASC) USING BTREE,
  UNIQUE INDEX `uk_email`(`email`(30) ASC) USING BTREE,
  UNIQUE INDEX `uk_phone_number`(`phone_number`(11) ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 39 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci ROW_FORMAT = DYNAMIC;

-- 插入测试账号数据
-- 注意：13812345678 为明文手机号，123456为明文密码
INSERT IGNORE INTO `user` (`user_name`, `email`, `phone_number`, `password`, `identity`) VALUES
('测试账号', 'test@example.com', '9e6cc4f19e70efd3cc3f5e3f4632fc2d', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', 'ADMIN');

-- ----------------------------
-- Table structure for winning_record
-- ----------------------------
create TABLE IF NOT EXISTS `winning_record`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT comment '主键',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP comment '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON update CURRENT_TIMESTAMP comment '更新时间',
  `activity_id` bigint NOT NULL comment '活动id',
  `activity_name` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL comment '活动名称',
  `prize_id` bigint NOT NULL comment '奖品id',
  `prize_name` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL comment '奖品名称',
  `prize_tier` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL comment '奖品等级',
  `winner_id` bigint NOT NULL comment '中奖人id',
  `winner_name` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL comment '中奖人姓名',
  `winner_email` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL comment '中奖人邮箱',
  `winner_phone_number` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL comment '中奖人电话',
  `winning_time` datetime NOT NULL comment '中奖时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_id`(`id` ASC) USING BTREE,
  UNIQUE INDEX `uk_activity_winner`(`activity_id` ASC, `winner_id` ASC) USING BTREE,
  INDEX `idx_activity_id`(`activity_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 69 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for dlx_message（死信消息持久化表）
-- ----------------------------
create TABLE IF NOT EXISTS `dlx_message`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT comment '主键',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP comment '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON update CURRENT_TIMESTAMP comment '更新时间',
  `message_id` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL comment '消息唯一标识',
  `message_body` text CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL comment '消息体（JSON格式）',
  `error_msg` varchar(1024) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL comment '异常原因',
  `retry_count` int NOT NULL DEFAULT 0 comment '已重试次数',
  `max_retry` int NOT NULL DEFAULT 3 comment '最大重试次数',
  `status` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL DEFAULT 'PENDING' comment '处理状态：PENDING-待处理, RETRYING-重试中, SUCCESS-处理成功, FAILED-人工处理',
  `next_retry_time` datetime NULL DEFAULT NULL comment '下次重试时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_id`(`id` ASC) USING BTREE,
  INDEX `idx_status_retry`(`status` ASC, `next_retry_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for demo_visit_session（演示访问会话统计表）
-- ----------------------------
create TABLE IF NOT EXISTS `demo_visit_session`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT comment '主键',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP comment '首次打开时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON update CURRENT_TIMESTAMP comment '最后活动时间',
  `session_id` char(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL comment '浏览器生成的匿名会话编号',
  `stayed_60_seconds` tinyint(1) NOT NULL DEFAULT 0 comment '是否停留超过60秒',
  `login_success` tinyint(1) NOT NULL DEFAULT 0 comment '是否登录成功',
  `draw_success` tinyint(1) NOT NULL DEFAULT 0 comment '是否完成抽奖',
  `error_count` int UNSIGNED NOT NULL DEFAULT 0 comment '页面异常次数',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_session_id`(`session_id` ASC) USING BTREE,
  INDEX `idx_gmt_create`(`gmt_create` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

-- SET FOREIGN_KEY_CHECKS = 1;：在脚本的最后，重新开启外键约束检查。
SET FOREIGN_KEY_CHECKS = 1;
