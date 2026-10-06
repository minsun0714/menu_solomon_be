ALTER TABLE team_members ADD COLUMN nickname VARCHAR(12) NOT NULL DEFAULT 'anon';

UPDATE team_members member
JOIN users user ON user.id = member.user_id
SET member.nickname = LEFT(user.nickname, 12);
