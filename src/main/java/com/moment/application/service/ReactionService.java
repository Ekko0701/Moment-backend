package com.moment.application.service;

import com.moment.application.port.in.ReactionUseCase;
import com.moment.application.port.out.PushPort;
import com.moment.application.port.out.PushPort.PushMessage;
import com.moment.application.port.out.ReactionPort;
import com.moment.application.port.out.MomentRepositoryPort;
import com.moment.application.port.out.UserRepositoryPort;
import com.moment.common.exception.BusinessException;
import com.moment.common.exception.ErrorCode;
import com.moment.domain.moment.Moment;
import com.moment.domain.moment.Reaction;
import com.moment.domain.user.User;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ReactionService implements ReactionUseCase {

    private final ReactionPort reactionRepository;
    private final MomentRepositoryPort momentRepository;
    private final UserRepositoryPort userRepository;
    private final PushPort pushPort;
    private final Clock clock;

    public ReactionService(
            ReactionPort reactionRepository,
            MomentRepositoryPort momentRepository,
            UserRepositoryPort userRepository,
            PushPort pushPort,
            Clock clock) {
        this.reactionRepository = reactionRepository;
        this.momentRepository = momentRepository;
        this.userRepository = userRepository;
        this.pushPort = pushPort;
        this.clock = clock;
    }

    @Override
    public void react(UUID meId, UUID momentId, String emoji) {
        // 모먼트 존재 및 미삭제 확인
        Moment moment = momentRepository.findById(momentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "모먼트를 찾을 수 없어요."));
        if (moment.isDeleted()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "삭제된 모먼트입니다.");
        }

        Instant now = clock.instant();
        Reaction newReaction = Reaction.create(UUID.randomUUID(), momentId, meId, emoji, now);

        // 기존 리액션 확인
        var existingReaction = reactionRepository.findByMomentIdAndUserId(momentId, meId);

        if (existingReaction.isPresent()) {
            Reaction existing = existingReaction.get();
            if (existing.emoji().equals(emoji)) {
                // 같은 이모지: 토글 취소 (삭제)
                reactionRepository.deleteById(existing.id());
            } else {
                // 다른 이모지: 교체
                reactionRepository.deleteById(existing.id());
                reactionRepository.save(newReaction);
                // 작성자에게 알림 (내 모먼트에 내가 리액션하면 알림 미발송)
                if (!moment.authorId().equals(meId)) {
                    notifyReactionAdded(moment.authorId(), meId, emoji);
                }
            }
        } else {
            // 신규 리액션: 저장
            reactionRepository.save(newReaction);
            // 작성자에게 알림 (내 모먼트에 내가 리액션하면 알림 미발송)
            if (!moment.authorId().equals(meId)) {
                notifyReactionAdded(moment.authorId(), meId, emoji);
            }
        }
    }

    @Override
    public void remove(UUID meId, UUID momentId) {
        var existingReaction = reactionRepository.findByMomentIdAndUserId(momentId, meId);
        if (existingReaction.isPresent()) {
            reactionRepository.deleteById(existingReaction.get().id());
        }
        // 없으면 무시
    }

    private void notifyReactionAdded(UUID momentAuthorId, UUID reactorId, String emoji) {
        User reactor = userRepository.findById(reactorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없어요."));

        pushPort.send(new PushMessage(
                momentAuthorId,
                "REACTION_ADDED",
                reactor.nickname() + "님이 " + emoji + "로 반응했어요.",
                "모먼트를 확인해 보세요.",
                Map.of("momentId", momentAuthorId.toString())));
    }
}
