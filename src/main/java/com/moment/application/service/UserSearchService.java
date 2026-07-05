package com.moment.application.service;

import com.moment.application.port.in.UserSearchUseCase;
import com.moment.application.port.out.BlockPort;
import com.moment.application.port.out.UserRepositoryPort;
import com.moment.common.exception.BusinessException;
import com.moment.common.exception.ErrorCode;
import com.moment.domain.user.User;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserSearchService implements UserSearchUseCase {

    private final UserRepositoryPort userRepository;
    private final BlockPort blockPort;

    public UserSearchService(UserRepositoryPort userRepository, BlockPort blockPort) {
        this.userRepository = userRepository;
        this.blockPort = blockPort;
    }

    @Override
    public User search(UUID meId, String handle) {
        // 자신검색불가
        User self = userRepository.findById(meId).orElse(null);
        if (self != null && self.handle().equals(handle)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "자기 자신은 검색할 수 없어요.");
        }

        // 핸들로사용자조회
        User target = userRepository.findByHandle(handle)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "해당 아이디의 사용자를 찾을 수 없어요."));

        // 비활성/검색미허용/차단관계는모두동일하게NOT_FOUND(존재여부은닉)
        if (!target.isActive()) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "해당 아이디의 사용자를 찾을 수 없어요.");
        }

        if (!target.searchable()) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "해당 아이디의 사용자를 찾을 수 없어요.");
        }

        if (blockPort.existsBlockBetween(meId, target.id())) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "해당 아이디의 사용자를 찾을 수 없어요.");
        }

        return target;
    }
}
