/*
Navicat MySQL Data Transfer

Source Server         : laser_temple
Source Server Version : 50717
Source Host           : 112.74.171.231:3306
Source Database       : zebra_dev

Target Server Type    : MYSQL
Target Server Version : 50717
File Encoding         : 65001

Date: 2019-09-04 11:40:56
*/

SET FOREIGN_KEY_CHECKS=0;

-- ----------------------------
-- Table structure for t_api_security
-- ----------------------------
DROP TABLE IF EXISTS `t_api_security`;
CREATE TABLE `t_api_security` (
  `api_key` varchar(25) CHARACTER SET utf8mb4 NOT NULL COMMENT 'api接口服务key',
  `api_secret` varchar(25) CHARACTER SET utf8mb4 NOT NULL COMMENT 'API接口服务secret',
  `api_status` tinyint(1) NOT NULL COMMENT 'api接口服务状态1正常 0禁用',
  `api_type` smallint(1) NOT NULL COMMENT 'API接口服务类型 1pc2公众号3小程序4移动端5APP',
  `api_create_time` datetime NOT NULL COMMENT '创建时间',
  `api_update_time` datetime NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`api_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='API权限认证表';

-- ----------------------------
-- Records of t_api_security
-- ----------------------------
INSERT INTO `t_api_security` VALUES ('key_2019usvlobd99!2', '56478989182564', '0', '2', '2019-08-20 16:57:11', '2019-08-21 12:44:03');
INSERT INTO `t_api_security` VALUES ('key_2019xertybd66!2', '2134567897686', '1', '5', '2019-08-20 17:24:20', '2019-08-20 17:24:20');
