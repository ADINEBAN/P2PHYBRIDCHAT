-- MySQL dump 10.13  Distrib 8.0.42, for Win64 (x86_64)
--
-- Host: localhost    Database: hybrid_messenger
-- ------------------------------------------------------
-- Server version	8.0.42

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `conversation_members`
--

DROP TABLE IF EXISTS `conversation_members`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `conversation_members` (
  `conversation_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `joined_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `last_seen_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `is_admin` tinyint(1) DEFAULT '0',
  `nickname` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`conversation_id`,`user_id`),
  KEY `user_id` (`user_id`),
  CONSTRAINT `conversation_members_ibfk_1` FOREIGN KEY (`conversation_id`) REFERENCES `conversations` (`id`),
  CONSTRAINT `conversation_members_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `conversation_members`
--

LOCK TABLES `conversation_members` WRITE;
/*!40000 ALTER TABLE `conversation_members` DISABLE KEYS */;
INSERT INTO `conversation_members` VALUES (1,1,'2025-12-19 01:21:18','2025-12-19 08:37:17',0,NULL),(1,2,'2025-12-19 01:21:18','2025-12-19 01:34:34',0,NULL),(2,1,'2025-12-19 01:21:18','2025-12-19 01:21:50',0,NULL),(2,2,'2025-12-19 01:21:18','2025-12-19 01:21:18',0,NULL),(3,1,'2025-12-19 01:34:57','2025-12-19 08:37:18',1,NULL),(3,2,'2025-12-19 01:34:57','2025-12-19 08:36:54',0,NULL);
/*!40000 ALTER TABLE `conversation_members` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `conversations`
--

DROP TABLE IF EXISTS `conversations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `conversations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_group` tinyint(1) DEFAULT '0',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `avatar_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `theme_color` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT '#ffffff',
  `background_image_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `conversations`
--

LOCK TABLES `conversations` WRITE;
/*!40000 ALTER TABLE `conversations` DISABLE KEYS */;
INSERT INTO `conversations` VALUES (1,NULL,0,'2025-12-19 01:21:18',NULL,'#E6E6E6',NULL),(2,NULL,0,'2025-12-19 01:21:18',NULL,'#ffffff',NULL),(3,'a',1,'2025-12-19 01:34:57',NULL,'#ffffff',NULL);
/*!40000 ALTER TABLE `conversations` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `friendships`
--

DROP TABLE IF EXISTS `friendships`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `friendships` (
  `user_id` bigint NOT NULL,
  `friend_id` bigint NOT NULL,
  `status` enum('PENDING','ACCEPTED','BLOCKED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'PENDING',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `nickname` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`user_id`,`friend_id`),
  KEY `friend_id` (`friend_id`),
  CONSTRAINT `friendships_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `friendships_ibfk_2` FOREIGN KEY (`friend_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `friendships`
--

LOCK TABLES `friendships` WRITE;
/*!40000 ALTER TABLE `friendships` DISABLE KEYS */;
INSERT INTO `friendships` VALUES (1,2,'ACCEPTED','2025-12-19 01:21:05',NULL),(2,1,'ACCEPTED','2025-12-19 01:21:15',NULL);
/*!40000 ALTER TABLE `friendships` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `hidden_messages`
--

DROP TABLE IF EXISTS `hidden_messages`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `hidden_messages` (
  `user_id` bigint NOT NULL,
  `message_id` bigint NOT NULL,
  PRIMARY KEY (`user_id`,`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `hidden_messages`
--

LOCK TABLES `hidden_messages` WRITE;
/*!40000 ALTER TABLE `hidden_messages` DISABLE KEYS */;
/*!40000 ALTER TABLE `hidden_messages` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `messages`
--

DROP TABLE IF EXISTS `messages`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `messages` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `conversation_id` bigint NOT NULL,
  `sender_id` bigint NOT NULL,
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `message_type` enum('TEXT','IMAGE','FILE') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'TEXT',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `attachment_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_pinned` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_msg_uuid` (`uuid`),
  KEY `conversation_id` (`conversation_id`),
  KEY `sender_id` (`sender_id`),
  CONSTRAINT `messages_ibfk_1` FOREIGN KEY (`conversation_id`) REFERENCES `conversations` (`id`),
  CONSTRAINT `messages_ibfk_2` FOREIGN KEY (`sender_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `messages`
--

LOCK TABLES `messages` WRITE;
/*!40000 ALTER TABLE `messages` DISABLE KEYS */;
INSERT INTO `messages` VALUES (1,'cf186277-5f2b-4624-ae35-94f2e6cac7ec',2,1,'e','TEXT','2025-12-19 01:21:07',NULL,0),(2,'b611d06e-fcff-4949-ad79-19b89dc169a0',2,1,'ê','TEXT','2025-12-19 01:21:11',NULL,0),(3,'049203cc-4240-4ec6-b448-2f07ce3c2673',2,1,'ư','TEXT','2025-12-19 01:21:26',NULL,0),(4,'9fd3753d-7892-4d32-ab6e-4e111172fa78',2,1,'ahaha','TEXT','2025-12-19 01:21:35',NULL,0),(5,'c6537590-1c7b-452e-abe3-6fe4f21fd468',1,1,'ê','TEXT','2025-12-19 01:29:50',NULL,0),(6,'e96a85a2-6944-4565-9a03-db820e205802',1,1,'dđ','TEXT','2025-12-19 01:29:53',NULL,0),(7,'1927fff0-3e39-492b-8a86-8bbb3df542a6',1,1,'dđ','TEXT','2025-12-19 01:29:58',NULL,0),(8,'9e8e4665-892b-4a85-a4b6-77fc14022724',1,2,'ok','TEXT','2025-12-18 18:32:11',NULL,0),(9,'d008008d-185c-4bb8-adb4-e0c2bd72a6a0',1,1,'?','TEXT','2025-12-19 01:32:43',NULL,0),(10,'fe542867-6aa0-46c7-94ab-8cc4f58e3fa3',1,1,'d','TEXT','2025-12-19 01:34:20',NULL,0),(11,'9fff6228-8dae-45c6-a4e2-843e41a40eb8',3,1,'ddd','TEXT','2025-12-19 01:34:47',NULL,0),(12,'ba8bbfff-572c-4983-9bbe-a1047d3788af',3,2,'hi','TEXT','2025-12-18 18:35:09',NULL,0),(13,'933f1691-0526-46cf-88db-3a31208cb180',3,2,'hi','TEXT','2025-12-19 01:36:49',NULL,0),(14,'66f7e808-a28a-4674-9d4c-ce66fcc7bb41',3,1,'kaka','TEXT','2025-12-19 01:36:54',NULL,0),(15,'dd70bb25-1d4d-4e70-9619-60e14cd99ab7',3,2,'kk','TEXT','2025-12-19 01:36:54',NULL,0);
/*!40000 ALTER TABLE `messages` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `password_hash` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `display_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_online` tinyint(1) DEFAULT '0',
  `last_ip` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_port` int DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `avatar_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status_msg` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'Đang hoạt động',
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT INTO `users` VALUES (1,'chung','$2a$12$hYIUk1FBsoNdhfZ4UZk7SOGFTSLYmzGw8rre0cZIAc3BioXxOBYA6','chungngo','chungngo269@gmail.com',0,'172.16.0.197',6284,'2025-12-19 01:20:44','avatars/1_99e87f22-9c5c-4554-98a6-a908525ce587.jpeg','Đang hoạt động'),(2,'zin','$2a$12$.PsxWrBh1vUmpinV8pX30uK06zSFon30Q.GpbaFZJQC2ebB9taQYa','zin','zinbya04@gmail.com',0,'172.16.0.171',6840,'2025-12-19 01:20:49','avatars/2_8de8a36f-e686-4178-9cb4-ea2bab4122c3.png','Đang hoạt động');
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2025-12-19 17:06:11
