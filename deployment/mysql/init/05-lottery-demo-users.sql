SET NAMES utf8mb4;
USE lottery_system;

-- 演示参与人员。手机号沿用项目 EncryptTypeHandler 的 AES 加密格式；邮箱使用保留域名，
-- 不会向真实收件人发信。需要演示真实邮件时，请在页面另行注册自己的邮箱。
INSERT IGNORE INTO `user` (`user_name`, `email`, `phone_number`, `password`, `identity`) VALUES
('陈晨', 'demo01@example.com', 'd7e11f1d0157c5c2a685fc4b23481a4d', NULL, 'NORMAL'),
('林悦', 'demo02@example.com', '310b282f168e7c874f3b7f144b713dd3', NULL, 'NORMAL'),
('周宇', 'demo03@example.com', '6766af672222b64174d37aa106c39ed7', NULL, 'NORMAL'),
('赵宁', 'demo04@example.com', '558a45eef7f1d88bf8733c3d4bcd2947', NULL, 'NORMAL'),
('刘思雨', 'demo05@example.com', 'f456125e78274c6ff12745f8cf9d6f57', NULL, 'NORMAL'),
('王嘉', 'demo06@example.com', '60d4e4ede90d14211148c4626067943b', NULL, 'NORMAL'),
('孙乐', 'demo07@example.com', '5e50cb2b5c72131eecf32a8406db4005', NULL, 'NORMAL'),
('李明', 'demo08@example.com', 'fda5cd962af32a22581d8c4c56fe6fb6', NULL, 'NORMAL'),
('吴桐', 'demo09@example.com', 'e4273adff4bb5b88d2ce79e7eb58a4d4', NULL, 'NORMAL'),
('郑欣', 'demo10@example.com', '6dd46d529b71a6156b57b7277e579e23', NULL, 'NORMAL'),
('冯凯', 'demo11@example.com', 'e122359e9d94b7c30ab860194e8c69b6', NULL, 'NORMAL'),
('许诺', 'demo12@example.com', 'c046ca2818eb5d56fc95ca17b6f10673', NULL, 'NORMAL'),
('唐可', 'demo13@example.com', 'e102bb03f2bc37115a0ba0e52377a672', NULL, 'NORMAL'),
('韩雪', 'demo14@example.com', '8d0e44ad26cd4151609ba63cedf492a0', NULL, 'NORMAL'),
('宋扬', 'demo15@example.com', '0d8f04d5c17c1a7e3deda99bac9a0478', NULL, 'NORMAL'),
('彭越', 'demo16@example.com', 'c713cb789608ab39a688affcb8affc44', NULL, 'NORMAL'),
('蒋文', 'demo17@example.com', '061b007efad9444c73ebb2e6a5e2825d', NULL, 'NORMAL'),
('罗晴', 'demo18@example.com', 'f441b8fed12a3d4c1075971890226cc9', NULL, 'NORMAL'),
('方舟', 'demo19@example.com', '15a0f682e22c3cc87c11020c275f7a28', NULL, 'NORMAL'),
('谢安', 'demo20@example.com', 'a991f73a5d77bf574d00206686cdc534', NULL, 'NORMAL');
