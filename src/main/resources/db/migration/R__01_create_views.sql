CREATE OR REPLACE VIEW user_details AS
  SELECT
    u.userid,
    u.username,
    #pa.password,
    u.firstname,
    u.lastname,
    u.expiry,
    u.email,
    u.activated,
    u.date_created,
    u.locked,
    u.temp_auth_key,
    p.state,
    p.city,
    p.organisation,
    p.primary_user_type,
    p.secondary_user_type,
    p.telephone,
    p.flickr_id,
    p.flickr_username,
    p.affiliation,
    ur.authority
  FROM users u
    JOIN (
           SELECT
             userid,
             group_concat(if(property = 'state', value, null)) as state,
             group_concat(if(property = 'city', value, null)) as city,
             group_concat(if(property = 'organisation', value, null)) as organisation,
             group_concat(if(property = 'primaryUserType', value, null)) as primary_user_type,
             group_concat(if(property = 'secondaryUserType', value, null)) as secondary_user_type,
             group_concat(if(property = 'telephone', value, null)) as telephone,
             group_concat(if(property = 'flickrId', value, null)) as flickr_id,
             group_concat(if(property = 'flickrUsername', value, null)) as flickr_username,
             group_concat(if(property = 'affiliation', value, null)) as affiliation
           FROM profiles
           GROUP BY userid
         ) p on u.userid = p.userid
    JOIN (
           SELECT
             user_id,
             group_concat(role_id) as authority
           FROM user_role
           GROUP BY user_id
         ) ur on u.userid = ur.user_id
    #LEFT OUTER JOIN passwords pa on u.userid = pa.userid and status = 'CURRENT'
;