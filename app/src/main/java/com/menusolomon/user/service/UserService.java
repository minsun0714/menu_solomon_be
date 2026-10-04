package com.menusolomon.user.service;

import com.menusolomon.user.dto.AnonymousIdentity;

public interface UserService {
    AnonymousIdentity identify(String rawToken);
}
