package com.hiking.gymsaas.account.service;

import com.hiking.gymsaas.common.security.Identity;

public interface IdentityService {
    Identity load(Identity.Type type, long id);
    Identity recheck(Identity identity, boolean lock);
}
