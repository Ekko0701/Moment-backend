package com.moment.adapter.out.persistence.user;

import com.moment.application.port.out.UserRepositoryPort;
import com.moment.domain.user.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class UserPersistenceAdapter implements UserRepositoryPort {

    private final UserJpaRepository jpaRepository;

    UserPersistenceAdapter(UserJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public User save(User user) {
        UserEntity entity = jpaRepository.findById(user.id())
                .map(existing -> {
                    existing.applyDomain(user);
                    return existing;
                })
                .orElseGet(() -> UserEntity.fromDomain(user));
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jpaRepository.findById(id).map(UserEntity::toDomain);
    }

    @Override
    public Optional<User> findByAppleSub(String appleSub) {
        return jpaRepository.findByAppleSub(appleSub).map(UserEntity::toDomain);
    }

    @Override
    public Optional<User> findByHandle(String handle) {
        return jpaRepository.findByHandle(handle).map(UserEntity::toDomain);
    }

    @Override
    public boolean existsByHandle(String handle) {
        return jpaRepository.existsByHandle(handle);
    }
}
