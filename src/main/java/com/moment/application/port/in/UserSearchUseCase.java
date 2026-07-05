package com.moment.application.port.in;

import com.moment.domain.user.User;
import java.util.UUID;

public interface UserSearchUseCase {

    User search(UUID meId, String handle);
}
