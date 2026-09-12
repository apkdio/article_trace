-- MySQL dump 10.13  Distrib 8.0.35, for Win64 (x86_64)
--
-- Host: localhost    Database: article_trace
-- ------------------------------------------------------
-- Server version	8.0.35

CREATE DATABASE IF NOT EXISTS `article_trace` 
DEFAULT CHARACTER SET utf8mb4 
COLLATE utf8mb4_unicode_ci;

USE `article_trace`;

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `article`
--

DROP TABLE IF EXISTS `article`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `article` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `title` varchar(30) NOT NULL COMMENT '文章标题',
  `content` varchar(50) NOT NULL COMMENT '文章内容',
  `cover_img` varchar(128) DEFAULT NULL COMMENT '文章封面',
  `state` int NOT NULL DEFAULT '0' COMMENT '文章状态: 只能是[已发布-1] 或者 [草稿-0]',
  `category_id` int COMMENT '文章分类ID',
  `create_user` int NOT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  `views` bigint DEFAULT '0' COMMENT '阅览数',
  PRIMARY KEY (`id`),
  KEY `fk_article_category` (`category_id`),
  KEY `fk_article_user` (`create_user`),
  CONSTRAINT `fk_article_category_id` FOREIGN KEY (`category_id`) REFERENCES `category` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_article_user` FOREIGN KEY (`create_user`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=65 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `category`
--

DROP TABLE IF EXISTS `category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `category` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `create_user` int NOT NULL COMMENT '创建人ID',
  `last_update_user` int DEFAULT NULL,
  `category_name` varchar(20) NOT NULL,
  `create_time` datetime NOT NULL,
  `update_time` datetime DEFAULT NULL,
  `category_alias` varchar(30) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_category_create_user` (`create_user`),
  KEY `fk_category_user_id` (`last_update_user`),
  CONSTRAINT `fk_category_create_user` FOREIGN KEY (`create_user`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_category_user_id` FOREIGN KEY (`last_update_user`) REFERENCES `user` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=31 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `comments`
--

DROP TABLE IF EXISTS `comments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `comments` (
  `id` int NOT NULL AUTO_INCREMENT,
  `article_id` int NOT NULL,
  `content` varchar(200) NOT NULL,
  `user_id` int NOT NULL,
  `create_time` datetime NOT NULL,
  `user_pic` varchar(128) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_comments_article_id` (`article_id`),
  KEY `fk_comments_user_id` (`user_id`),
  CONSTRAINT `fk_comments_article_id` FOREIGN KEY (`article_id`) REFERENCES `article` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_comments_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user`
--

DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `username` varchar(20) NOT NULL COMMENT '用户名',
  `password` varchar(60) DEFAULT NULL COMMENT '密码',
  `nickname` varchar(15) DEFAULT '' COMMENT '昵称',
  `email` varchar(128) DEFAULT '' COMMENT '邮箱',
  `user_pic` varchar(128) DEFAULT '' COMMENT '头像',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  `last_login` datetime DEFAULT NULL COMMENT '最后登录时间',
  `reset_pass` varchar(60) NOT NULL,
  `type` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=52 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `notification`
--

DROP TABLE IF EXISTS `notification`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notification` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `title` varchar(120) NOT NULL COMMENT '标题',
  `sender_id` int DEFAULT NULL COMMENT '发送方: -1=系统消息; 正整数=用户ID; NULL=发送方已注销',
  `receiver_id` int DEFAULT NULL COMMENT '接收方: 用户ID; NULL=接收方已注销',
  `content` text COMMENT '正文',
  `create_time` datetime NOT NULL COMMENT '发送时间',
  `is_read` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否已读: 0-未读 1-已读',
  PRIMARY KEY (`id`),
  KEY `idx_receiver_read_time` (`receiver_id`,`is_read`,`create_time`),
  KEY `idx_notification_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站内信';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `notification_mail`
--

DROP TABLE IF EXISTS `notification_mail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notification_mail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `to_email` varchar(128) NOT NULL COMMENT '收件邮箱',
  `subject` varchar(200) NOT NULL COMMENT '主题',
  `content` text COMMENT '正文',
  `status` varchar(16) NOT NULL DEFAULT 'pending' COMMENT '状态: pending/sent/failed',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '已重试次数',
  `error` varchar(500) DEFAULT NULL COMMENT '失败原因',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `sent_time` datetime DEFAULT NULL COMMENT '发送成功时间',
  PRIMARY KEY (`id`),
  KEY `idx_mail_status_retry` (`status`,`retry_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邮件投递记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `author_apply`
--

DROP TABLE IF EXISTS `author_apply`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `author_apply` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `user_id` int NOT NULL COMMENT '申请人 user.id',
  `reason` varchar(500) DEFAULT NULL COMMENT '申请理由',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态: 0-待审 1-通过 2-拒绝',
  `reject_reason` varchar(255) DEFAULT NULL COMMENT '拒绝原因',
  `review_user` int DEFAULT NULL COMMENT '审批人 user.id',
  `review_time` datetime DEFAULT NULL COMMENT '审批时间',
  `create_time` datetime NOT NULL COMMENT '提交时间',
  PRIMARY KEY (`id`),
  KEY `idx_status_time` (`status`,`create_time`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='作者申请';
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-03-28 14:41:52
