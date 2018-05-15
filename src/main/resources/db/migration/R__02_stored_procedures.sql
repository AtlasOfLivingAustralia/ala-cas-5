DROP PROCEDURE IF EXISTS `sp_get_user_attributes`;
DELIMITER //
CREATE PROCEDURE `sp_get_user_attributes`(p_username varchar(255))
  BEGIN
    SELECT 'email' AS 'key', email AS 'value' FROM users WHERE username=p_username
    UNION SELECT 'firstname' AS 'key', firstname AS 'value' FROM users WHERE username=p_username
    UNION SELECT 'givenName' AS 'key', firstname AS 'value' FROM users WHERE username=p_username
    UNION SELECT 'lastname' AS 'key', lastname AS 'value' FROM users WHERE username=p_username
    UNION SELECT 'sn' AS 'key', lastname AS 'value' FROM users WHERE username=p_username
    UNION SELECT 'userid' AS 'key', cast(userid AS char) AS 'value' FROM users WHERE username=p_username
    UNION SELECT 'id' AS 'key', cast(userid AS char) AS 'value' FROM users WHERE username=p_username
    UNION SELECT 'authority' AS 'key', group_concat(a.role_id) AS 'value' FROM user_role a JOIN users u ON a.user_id=u.userid WHERE u.username=p_username HAVING value IS NOT NULL
    UNION SELECT 'role' AS 'key', a.role_id AS 'value' FROM user_role a JOIN users u ON a.user_id=u.userid WHERE u.username=p_username
    UNION SELECT 'created' as 'key', created as 'value' FROM users WHERE username=p_username
    UNION SELECT 'activated' as 'key', activated as 'value' FROM users WHERE username=p_username
    UNION SELECT 'disabled' as 'key', locked as 'value' FROM users WHERE username=p_username
    UNION SELECT 'expired' as 'key', COALESCE(expiry < CURRENT_TIMESTAMP(), FALSE) as 'value' FROM users WHERE username=p_username
    UNION SELECT p.property AS 'key', p.value FROM users u LEFT OUTER JOIN `profiles` p on u.userid=p.userid WHERE u.username=p_username;
  end
//
DELIMITER ;


DROP PROCEDURE IF EXISTS `sp_get_user_authorities`;
DELIMITER //
CREATE PROCEDURE sp_get_user_authorities()
  BEGIN
    SELECT username, group_concat(ur.role_id) AS 'authorities' FROM users u JOIN user_role ur ON u.userid = ur.user_id GROUP BY username;
  end
//
DELIMITER ;

DROP PROCEDURE IF EXISTS `sp_create_user`;
DELIMITER //
CREATE PROCEDURE `sp_create_user`(
  IN `email`              varchar(255),
  IN `firstname`          varchar(255),
  IN `lastname`           varchar(255),
  IN `password`           varchar(255),
  IN `city`               varchar(255),
  IN `organisation`       varchar(255),
  IN `primaryUserType`    varchar(255),
  IN `secondaryUserType`  varchar(255),
  IN `ausstate`           varchar(255),
  IN `telephone`          varchar(255),
  OUT `user_id`            int(11))
  BEGIN
    DECLARE new_id int(11);
    INSERT INTO `users` (`username`, `firstname`, `lastname`, `email`, `activated`, `locked`) VALUES (email, firstname, lastname, email, '1', '0');
    SET new_id = LAST_INSERT_ID();
    INSERT INTO `passwords` (`userid`, `password`, `status`, `type`) VALUES (new_id, password, 'CURRENT', 'bcrypt');
    INSERT INTO `user_role` (`user_id`, `role_id`) VALUES (new_id, 'ROLE_USER');
    INSERT INTO `profiles` (`userid`, `property`, `value`) VALUES (new_id, 'city',              city);
    INSERT INTO `profiles` (`userid`, `property`, `value`) VALUES (new_id, 'organisation',      organisation);
    INSERT INTO `profiles` (`userid`, `property`, `value`) VALUES (new_id, 'primaryUserType',   primaryUserType);
    INSERT INTO `profiles` (`userid`, `property`, `value`) VALUES (new_id, 'secondaryUserType', secondaryUserType);
    INSERT INTO `profiles` (`userid`, `property`, `value`) VALUES (new_id, 'state',             ausstate);
    INSERT INTO `profiles` (`userid`, `property`, `value`) VALUES (new_id, 'telephone',         telephone);
    SELECT new_id INTO `user_id`;
  END
//
DELIMITER ;
