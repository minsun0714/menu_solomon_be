-- Current Entity mappings; run against an empty MySQL database.
CREATE DATABASE IF NOT EXISTS menu_solomon CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE menu_solomon;

CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    anonymous_token_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_anonymous_token_hash (anonymous_token_hash)
) ENGINE=InnoDB;

CREATE TABLE teams (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255) NOT NULL,
    invite_token VARCHAR(255) NOT NULL,
    office_kakao_place_id VARCHAR(255),
    office_name VARCHAR(255),
    office_address VARCHAR(255),
    office_latitude DECIMAL(10,7),
    office_longitude DECIMAL(10,7),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_team_invite_token (invite_token)
) ENGINE=InnoDB;

CREATE TABLE team_members (
    id BIGINT NOT NULL AUTO_INCREMENT,
    team_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role ENUM('ADMIN','MEMBER') NOT NULL,
    joined_at DATETIME(6) NOT NULL,
    left_at DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_team_member (team_id, user_id)
) ENGINE=InnoDB;

CREATE TABLE restaurants (
    id BIGINT NOT NULL AUTO_INCREMENT,
    kakao_place_id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(255) NOT NULL,
    latitude DECIMAL(10,7) NOT NULL,
    longitude DECIMAL(10,7) NOT NULL,
    category VARCHAR(255) NOT NULL,
    kakao_place_url VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_restaurant_kakao_place_id (kakao_place_id)
) ENGINE=InnoDB;

CREATE TABLE team_restaurants (
    id BIGINT NOT NULL AUTO_INCREMENT,
    team_id BIGINT NOT NULL,
    restaurant_id BIGINT NOT NULL,
    registered_by_team_member_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_team_restaurant (team_id, restaurant_id)
) ENGINE=InnoDB;

CREATE TABLE reviews (
    id BIGINT NOT NULL AUTO_INCREMENT,
    team_restaurant_id BIGINT NOT NULL,
    team_member_id BIGINT NOT NULL,
    rating TINYINT NOT NULL,
    content VARCHAR(200) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_review_restaurant_member (team_restaurant_id, team_member_id),
    KEY idx_review_restaurant_updated (team_restaurant_id, updated_at, id)
) ENGINE=InnoDB;

CREATE TABLE lunch_vote_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    team_id BIGINT NOT NULL,
    name VARCHAR(40),
    status ENUM('OPEN','CLOSED','CONFIRMED') NOT NULL,
    created_by_team_member_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    closes_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_vote_expiry (status, closes_at)
) ENGINE=InnoDB;

CREATE TABLE lunch_candidates (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    restaurant_id BIGINT NOT NULL,
    source ENUM('MANUAL','RECOMMENDED') NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_lunch_candidates (session_id, restaurant_id)
) ENGINE=InnoDB;

CREATE TABLE lunch_participants (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    team_member_id BIGINT NOT NULL,
    participating BIT(1) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_lunch_participants (session_id, team_member_id)
) ENGINE=InnoDB;

CREATE TABLE lunch_ballots (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    candidate_id BIGINT NOT NULL,
    team_member_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_lunch_ballots (session_id, candidate_id, team_member_id)
) ENGINE=InnoDB;

CREATE TABLE lunch_decisions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    restaurant_id BIGINT NOT NULL,
    confirmed_by_team_member_id BIGINT,
    confirmation_type ENUM('AUTO','MANUAL') NOT NULL,
    confirmed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_lunch_decisions (session_id)
) ENGINE=InnoDB;
