SET NAMES utf8mb4;
USE lottery_system;

-- 演示奖品按低、中、高价位递增，便于 AI 根据不同预算组合奖项。
-- 使用名称判重，既适用于全新数据卷，也可安全地手动补充到已有数据库。
INSERT INTO prize (name, description, price, image_url)
SELECT '便携暖手宝', '轻巧便携的充电暖手宝，适合作为冬日关怀、签到小礼或参与奖。', 19.00, 'prize-hand-warmer.jpg'
WHERE NOT EXISTS (SELECT 1 FROM prize WHERE name = '便携暖手宝');

INSERT INTO prize (name, description, price, image_url)
SELECT '香薰蜡烛礼盒', '双杯香薰蜡烛礼盒，适合节日活动、员工关怀和轻松氛围主题抽奖。', 39.00, 'prize-candle-gift-set.jpg'
WHERE NOT EXISTS (SELECT 1 FROM prize WHERE name = '香薰蜡烛礼盒');

INSERT INTO prize (name, description, price, image_url)
SELECT '随行保温杯', '简约耐用的随行保温杯，适合办公、通勤和健康生活主题活动。', 69.00, 'prize-tumbler.jpg'
WHERE NOT EXISTS (SELECT 1 FROM prize WHERE name = '随行保温杯');

INSERT INTO prize (name, description, price, image_url)
SELECT '便携蓝牙音箱', '小巧便携的无线音箱，适合年会、校园和年轻化团队活动。', 99.00, 'prize-bluetooth-speaker.jpg'
WHERE NOT EXISTS (SELECT 1 FROM prize WHERE name = '便携蓝牙音箱');

INSERT INTO prize (name, description, price, image_url)
SELECT '护眼折叠台灯', '可折叠的柔光护眼台灯，适合办公学习、读书会和员工福利活动。', 159.00, 'prize-desk-lamp.jpg'
WHERE NOT EXISTS (SELECT 1 FROM prize WHERE name = '护眼折叠台灯');

INSERT INTO prize (name, description, price, image_url)
SELECT '便携榨汁杯', '可随身携带的充电榨汁杯，适合夏日、运动和健康主题抽奖。', 249.00, 'prize-portable-blender.jpg'
WHERE NOT EXISTS (SELECT 1 FROM prize WHERE name = '便携榨汁杯');

INSERT INTO prize (name, description, price, image_url)
SELECT '颈部按摩仪', '轻便舒适的颈部按摩仪，适合职场关怀、周年庆和年会抽奖。', 399.00, 'prize-neck-massager.jpg'
WHERE NOT EXISTS (SELECT 1 FROM prize WHERE name = '颈部按摩仪');

INSERT INTO prize (name, description, price, image_url)
SELECT '头戴式无线耳机', '沉浸舒适的头戴式无线耳机，适合音乐、通勤和科技主题活动。', 599.00, 'prize-wireless-headphones.jpg'
WHERE NOT EXISTS (SELECT 1 FROM prize WHERE name = '头戴式无线耳机');

INSERT INTO prize (name, description, price, image_url)
SELECT '智能空气炸锅', '实用精致的智能空气炸锅，适合家庭生活、节日福利和年会抽奖。', 899.00, 'prize-air-fryer.jpg'
WHERE NOT EXISTS (SELECT 1 FROM prize WHERE name = '智能空气炸锅');

INSERT INTO prize (name, description, price, image_url)
SELECT '智能扫地机器人', '省心实用的智能扫地机器人，适合作为周年庆或年会高价值奖品。', 1299.00, 'prize-robot-vacuum.jpg'
WHERE NOT EXISTS (SELECT 1 FROM prize WHERE name = '智能扫地机器人');

INSERT INTO prize (name, description, price, image_url)
SELECT '高清智能投影仪', '适合家庭影音和轻办公场景的高清智能投影仪，可作为活动核心大奖。', 1999.00, 'prize-smart-projector.jpg'
WHERE NOT EXISTS (SELECT 1 FROM prize WHERE name = '高清智能投影仪');

INSERT INTO prize (name, description, price, image_url)
SELECT '轻薄办公笔记本', '兼顾移动办公与日常娱乐的轻薄笔记本，适合作为大型活动一等奖。', 3999.00, 'prize-lightweight-laptop.jpg'
WHERE NOT EXISTS (SELECT 1 FROM prize WHERE name = '轻薄办公笔记本');
