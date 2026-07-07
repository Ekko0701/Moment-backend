package com.moment.application.port.out;

import com.moment.domain.user.User;
import java.util.Optional;
import java.util.UUID;

public interface UserRepositoryPort {

    User save(User user);

    Optional<User> findById(UUID id);

    Optional<User> findByAppleSub(String appleSub);

    Optional<User> findByHandle(String handle);

    boolean existsByHandle(String handle);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
