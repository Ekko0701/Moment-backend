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

    /**
     * 주어진 사용자 행들을 id 오름차순으로 비관적 쓰기 잠금한다.
     * 연결 성립 시 두 참여자를 일정한 순서로 잠가, 서로 다른 초대라도 같은 사용자를
     * 공유하면 직렬화되어 1인 1스페이스 불변식이 동시성 하에서도 유지된다(데드락 회피).
     */
    List<User> lockAllByIdsForUpdate(List<UUID> ids);

    Optional<User> findByAppleSub(String appleSub);

    Optional<User> findByHandle(String handle);

    boolean existsByHandle(String handle);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
