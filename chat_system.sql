-- phpMyAdmin SQL Dump
-- version 5.2.1
-- https://www.phpmyadmin.net/
--
-- Host: 127.0.0.1
-- Generation Time: Apr 09, 2025 at 03:31 AM
-- Server version: 10.4.32-MariaDB
-- PHP Version: 8.2.12

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Database: `chat_system`
--

-- --------------------------------------------------------

--
-- Table structure for table `files`
--

CREATE TABLE `files` (
  `id` int(11) NOT NULL,
  `file_name` varchar(255) NOT NULL,
  `sender_id` int(11) NOT NULL,
  `receiver_id` int(11) DEFAULT NULL,
  `is_group` tinyint(1) DEFAULT 0,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `files`
--

INSERT INTO `files` (`id`, `file_name`, `sender_id`, `receiver_id`, `is_group`, `created_at`) VALUES
(2, 'note.txt', 1, 3, 0, '2025-04-08 08:14:47');

-- --------------------------------------------------------

--
-- Table structure for table `friends`
--

CREATE TABLE `friends` (
  `user_id` int(11) NOT NULL,
  `friend_id` int(11) NOT NULL,
  `status` enum('pending','accepted','blocked') DEFAULT 'pending',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `friends`
--

INSERT INTO `friends` (`user_id`, `friend_id`, `status`, `created_at`) VALUES
(1, 2, 'accepted', '2025-03-20 16:11:31'),
(1, 3, 'accepted', '2025-03-20 16:11:31'),
(2, 1, 'accepted', '2025-03-20 16:11:31'),
(2, 3, 'accepted', '2025-03-20 16:11:31'),
(3, 1, 'accepted', '2025-03-20 16:11:31'),
(3, 2, 'accepted', '2025-03-20 16:11:31');

-- --------------------------------------------------------

--
-- Table structure for table `group_chats`
--

CREATE TABLE `group_chats` (
  `id` int(11) NOT NULL,
  `group_name` varchar(100) NOT NULL,
  `creator_id` int(11) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `group_chats`
--

INSERT INTO `group_chats` (`id`, `group_name`, `creator_id`, `created_at`) VALUES
(1, 'Nhóm Bạn Bè', 1, '2025-03-20 16:22:22');

-- --------------------------------------------------------

--
-- Table structure for table `group_members`
--

CREATE TABLE `group_members` (
  `group_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `role` enum('admin','member') DEFAULT 'member',
  `joined_at` timestamp NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `group_members`
--

INSERT INTO `group_members` (`group_id`, `user_id`, `role`, `joined_at`) VALUES
(1, 1, 'admin', '2025-03-20 16:22:26'),
(1, 2, 'member', '2025-03-20 16:22:26'),
(1, 3, 'member', '2025-03-20 16:22:26');

-- --------------------------------------------------------

--
-- Table structure for table `group_messages`
--

CREATE TABLE `group_messages` (
  `id` int(11) NOT NULL,
  `group_id` int(11) DEFAULT NULL,
  `sender_id` int(11) DEFAULT NULL,
  `content` text NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `group_messages`
--

INSERT INTO `group_messages` (`id`, `group_id`, `sender_id`, `content`, `created_at`) VALUES
(1, 1, 1, 'Chào mọi người!', '2025-03-20 16:22:31'),
(2, 1, 2, 'Chào Quân, Huyền!', '2025-03-20 16:22:31'),
(3, 1, 3, 'Mọi người có khỏe không?', '2025-03-20 16:22:31'),
(4, 1, 1, 'Nhờ phúc của bạn mà tôi rất khỏe :))', '2025-03-20 17:30:20'),
(5, 1, 3, 'Nghe giọng thấy ghét à nha <::', '2025-03-20 17:38:06'),
(6, 1, 1, 'Hello', '2025-03-21 09:52:38'),
(7, 1, 2, 'Hello', '2025-03-21 10:01:26'),
(8, 1, 2, 'Mai đi chơi chứ mọi người', '2025-03-21 10:01:51'),
(9, 1, 3, 'Hỏi Quân đi', '2025-03-21 10:06:52'),
(10, 1, 1, 'Đây Quân đây', '2025-03-21 10:37:08'),
(11, 1, 1, 'Nói đi là đi thôi haha', '2025-03-21 10:37:15'),
(12, 1, 1, 'Alo', '2025-03-21 11:14:57'),
(13, 1, 1, 'Alo', '2025-03-21 15:08:55'),
(14, 1, 3, 'Gì mà alo lắm thế ông cụ', '2025-03-21 15:09:55'),
(15, 1, 1, 'Gọi không ai thưa đó bạn :))', '2025-03-21 15:10:19'),
(16, 1, 3, 'Có gì cho bạn ko mà gọi giờ này :))', '2025-03-21 15:10:44'),
(17, 1, 2, 'Alo', '2025-03-21 16:50:36'),
(18, 1, 1, 'Cos bạn ơi', '2025-03-21 16:50:48'),
(19, 1, 3, 'Hello', '2025-03-21 16:52:19');

-- --------------------------------------------------------

--
-- Table structure for table `messages`
--

CREATE TABLE `messages` (
  `id` int(11) NOT NULL,
  `sender_id` int(11) DEFAULT NULL,
  `receiver_id` int(11) DEFAULT NULL,
  `content` text NOT NULL,
  `is_read` tinyint(1) DEFAULT 0,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `messages`
--

INSERT INTO `messages` (`id`, `sender_id`, `receiver_id`, `content`, `is_read`, `created_at`) VALUES
(1, 1, 2, 'Huyền ơi, có thể gặp nhau không?', 0, '2025-03-20 16:22:35'),
(2, 2, 1, 'Có chứ, khi nào?', 0, '2025-03-20 16:22:35'),
(3, 3, 1, 'Trung muốn đi chơi không?', 0, '2025-03-20 16:22:35'),
(4, 1, 3, 'Đi thôi!', 0, '2025-03-20 16:22:35'),
(5, 1, 2, 'Ngày mai nhé, có được không', 0, '2025-03-20 17:04:56'),
(6, 1, 3, 'Có cần tôi đến chở không', 0, '2025-03-20 17:05:28'),
(7, 2, 3, 'Hello Trung', 0, '2025-03-20 17:06:29'),
(8, 2, 3, 'Quân hôm nay có đi hoc không Trung', 0, '2025-03-20 17:06:53'),
(9, 3, 1, 'Có, chút qua chở tôi nhé :))', 0, '2025-03-20 17:37:13'),
(10, 3, 2, 'Không bạn ơi, nay ko thấy ông ấy đi học', 0, '2025-03-20 17:37:30'),
(11, 1, 2, 'Hello', 0, '2025-03-21 09:52:59'),
(12, 1, 3, 'Hello', 0, '2025-03-21 09:53:12'),
(13, 2, 1, 'Tớ đây, có chuyền gì thế', 0, '2025-03-21 09:59:18'),
(14, 2, 1, 'Tớ đây, có chuyện gì thế', 0, '2025-03-21 09:59:56'),
(15, 1, 2, 'Còn thức không bạn ơi', 0, '2025-03-21 10:02:36'),
(16, 2, 1, 'Có vẫn còn thức đây', 0, '2025-03-21 10:02:48'),
(17, 2, 1, 'Vẫn còn thức nè', 0, '2025-03-21 10:03:31'),
(18, 2, 1, 'Có vẫn còn thức nè', 0, '2025-03-21 10:03:49'),
(19, 3, 1, 'Có bạn ơi', 0, '2025-03-21 10:06:09'),
(20, 3, 1, 'Có bạn ơi', 0, '2025-03-21 10:07:26'),
(21, 3, 1, 'Có bạn ơi', 0, '2025-03-21 10:07:42'),
(22, 3, 1, 'Có bạn ơi', 0, '2025-03-21 10:12:19'),
(23, 3, 1, 'Chơi kì à nha', 0, '2025-03-21 10:13:01'),
(24, 3, 1, 'Vãi đạn thật chư tin nhắn hiển thị hay ta', 0, '2025-03-21 10:13:29'),
(25, 1, 3, 'Sao nó lại loạn thế này ta', 0, '2025-03-21 10:19:10'),
(26, 3, 1, 'Sao giờ lại được rồi này', 0, '2025-03-21 10:19:47'),
(27, 3, 1, 'Ủa hay nha', 0, '2025-03-21 10:20:36'),
(28, 3, 1, 'Được rồi đấy anh vừa sửa lại rồi', 0, '2025-03-21 10:36:27'),
(29, 1, 3, 'Ok anh iu', 0, '2025-03-21 10:36:41'),
(30, 3, 2, 'Vậy tớ cảm ơn nhé', 0, '2025-03-21 10:43:54'),
(31, 2, 3, 'Vậy tớ cảm ơn nhé', 0, '2025-03-21 10:44:17'),
(32, 2, 3, 'Hôm nay gọi không được nền hỏi cậu xem Quân có trên lớp không', 0, '2025-03-21 10:44:38'),
(33, 3, 2, 'Oki không có gì nhé', 0, '2025-03-21 10:44:52'),
(34, 1, 2, 'Thôi ngủ tiếp đi :))', 0, '2025-03-21 11:15:30'),
(35, 1, 3, 'Bye Bye', 0, '2025-03-21 11:41:40'),
(36, 1, 3, 'Alo', 0, '2025-03-21 14:40:40'),
(37, 3, 1, 'Hi', 0, '2025-03-21 14:41:05'),
(38, 1, 3, 'Hi', 0, '2025-03-21 14:50:10'),
(39, 1, 2, 'Hello', 0, '2025-03-21 14:53:56'),
(40, 1, 3, 'Lo', 0, '2025-03-21 14:54:48'),
(41, 3, 1, 'Có bạn ây', 0, '2025-03-21 15:10:58'),
(42, 1, 3, 'Đi chơi không', 0, '2025-03-21 15:11:06'),
(43, 3, 1, 'Đi đâu', 0, '2025-03-21 15:11:17'),
(44, 2, 1, 'Có tui bạn ây', 0, '2025-03-21 15:21:34'),
(45, 1, 2, 'Đi chơi ko bạn ây :))', 0, '2025-03-21 15:21:48'),
(46, 2, 1, 'Có bạn ây', 0, '2025-03-21 15:21:58'),
(47, 2, 1, 'Alo', 0, '2025-03-21 16:50:11'),
(48, 3, 1, 'di net', 0, '2025-04-01 03:17:59'),
(49, 1, 3, 'ok', 0, '2025-04-01 03:20:28'),
(50, 3, 1, 'ok', 0, '2025-04-01 03:20:41'),
(51, 3, 1, 'khó quá ae', 0, '2025-04-02 02:49:31'),
(52, 3, 1, 'aloo', 0, '2025-04-06 20:21:44'),
(53, 3, 1, 'test', 0, '2025-04-06 21:44:22'),
(54, 3, 1, 'alo', 0, '2025-04-08 04:12:50'),
(55, 3, 1, 'hi', 0, '2025-04-08 04:32:12'),
(56, 1, 3, '[FILE] note.txt', 0, '2025-04-08 08:14:47'),
(57, 3, 1, 'ok chưa', 0, '2025-04-08 09:29:52');

-- --------------------------------------------------------

--
-- Table structure for table `users`
--

CREATE TABLE `users` (
  `id` int(11) NOT NULL,
  `username` varchar(50) NOT NULL,
  `email` varchar(100) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `avatar_url` varchar(255) DEFAULT NULL,
  `status` enum('online','offline','busy') DEFAULT 'offline',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `users`
--

INSERT INTO `users` (`id`, `username`, `email`, `password_hash`, `avatar_url`, `status`, `created_at`) VALUES
(1, 'Trần Thế Quân', 'Tranquan@gmail.com', '5994471abb01112afcc18159f6cc74b4f511b99806da59b3caf5a9c173cacfc5', NULL, 'online', '2025-03-20 14:07:10'),
(2, 'Nguyễn Ngọc Huyền', 'Ngochuyen@gmail.com', '5994471abb01112afcc18159f6cc74b4f511b99806da59b3caf5a9c173cacfc5', NULL, 'offline', '2025-03-20 14:25:44'),
(3, 'Nguyễn Minh Trung', 'Minhtrung@gmail.com', '5994471abb01112afcc18159f6cc74b4f511b99806da59b3caf5a9c173cacfc5', NULL, 'offline', '2025-03-20 15:08:44');

--
-- Indexes for dumped tables
--

--
-- Indexes for table `files`
--
ALTER TABLE `files`
  ADD PRIMARY KEY (`id`),
  ADD KEY `sender_id` (`sender_id`),
  ADD KEY `files_receiver_fk` (`receiver_id`);

--
-- Indexes for table `friends`
--
ALTER TABLE `friends`
  ADD PRIMARY KEY (`user_id`,`friend_id`),
  ADD KEY `friend_id` (`friend_id`);

--
-- Indexes for table `group_chats`
--
ALTER TABLE `group_chats`
  ADD PRIMARY KEY (`id`),
  ADD KEY `creator_id` (`creator_id`);

--
-- Indexes for table `group_members`
--
ALTER TABLE `group_members`
  ADD PRIMARY KEY (`group_id`,`user_id`),
  ADD KEY `user_id` (`user_id`);

--
-- Indexes for table `group_messages`
--
ALTER TABLE `group_messages`
  ADD PRIMARY KEY (`id`),
  ADD KEY `group_id` (`group_id`),
  ADD KEY `sender_id` (`sender_id`);

--
-- Indexes for table `messages`
--
ALTER TABLE `messages`
  ADD PRIMARY KEY (`id`),
  ADD KEY `sender_id` (`sender_id`),
  ADD KEY `receiver_id` (`receiver_id`);

--
-- Indexes for table `users`
--
ALTER TABLE `users`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `username` (`username`),
  ADD UNIQUE KEY `email` (`email`);

--
-- AUTO_INCREMENT for dumped tables
--

--
-- AUTO_INCREMENT for table `files`
--
ALTER TABLE `files`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=3;

--
-- AUTO_INCREMENT for table `group_chats`
--
ALTER TABLE `group_chats`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=2;

--
-- AUTO_INCREMENT for table `group_messages`
--
ALTER TABLE `group_messages`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=20;

--
-- AUTO_INCREMENT for table `messages`
--
ALTER TABLE `messages`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=58;

--
-- AUTO_INCREMENT for table `users`
--
ALTER TABLE `users`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=4;

--
-- Constraints for dumped tables
--

--
-- Constraints for table `files`
--
ALTER TABLE `files`
  ADD CONSTRAINT `files_ibfk_1` FOREIGN KEY (`sender_id`) REFERENCES `users` (`id`),
  ADD CONSTRAINT `files_receiver_fk` FOREIGN KEY (`receiver_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;

--
-- Constraints for table `friends`
--
ALTER TABLE `friends`
  ADD CONSTRAINT `friends_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `friends_ibfk_2` FOREIGN KEY (`friend_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;

--
-- Constraints for table `group_chats`
--
ALTER TABLE `group_chats`
  ADD CONSTRAINT `group_chats_ibfk_1` FOREIGN KEY (`creator_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;

--
-- Constraints for table `group_members`
--
ALTER TABLE `group_members`
  ADD CONSTRAINT `group_members_ibfk_1` FOREIGN KEY (`group_id`) REFERENCES `group_chats` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `group_members_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;

--
-- Constraints for table `group_messages`
--
ALTER TABLE `group_messages`
  ADD CONSTRAINT `group_messages_ibfk_1` FOREIGN KEY (`group_id`) REFERENCES `group_chats` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `group_messages_ibfk_2` FOREIGN KEY (`sender_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;

--
-- Constraints for table `messages`
--
ALTER TABLE `messages`
  ADD CONSTRAINT `messages_ibfk_1` FOREIGN KEY (`sender_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `messages_ibfk_2` FOREIGN KEY (`receiver_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
