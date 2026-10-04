package com.menusolomon.user.service;

import com.menusolomon.user.domain.User;

public interface UserService {

    User getOrCreateBySessionToken(String rawSessionToken);
}
