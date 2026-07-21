package com.moment.application.port.out;

import com.moment.domain.user.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepositoryPort {

    User save(User user);

    Optional<User> findById(UUID id);

    /** 여러 사용자를 한 번에 조회한다(타임라인 작성자 일괄 로딩용, N+1 방지). */
    List<User> findAllByIds(Collection<UUID> ids);

    Optional<User> findByAppleSub(String appleSub);

    Optional<User> findByHandle(String handle);

    boolean existsByHandle(String handle);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
