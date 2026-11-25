package com.moment.adapter.in.web.dto.moment;

import com.moment.application.port.in.MomentUseCase.MomentView;
import java.util.List;
import java.util.UUID;

public record MomentResponse(
    UUID id,
    UUID spaceId,
    AuthorResponse author,
    String imageUrl,
    String text,
    String textPosition,
    String textColor,
    long createdAtMs,
    List<ReactionCountResponse> reactions,
    String myReaction) {

  public static MomentResponse from(MomentView view) {
    return new MomentResponse(
        view.id(),
        view.spaceId(),
        new AuthorResponse(
            view.author().userId(),
            view.author().handle(),
            view.author().nickname(),
            view.author().profileImageUrl()),
        view.imageUrl(),
        view.text(),
        view.textPosition() != null ? view.textPosition().name() : null,
        view.textColor() != null ? view.textColor().name() : null,
        view.createdAtMs(),
        view.reactions().stream()
            .map(r -> new ReactionCountResponse(r.emoji(), r.count()))
            .toList(),
        view.myReaction());
  }

  public record AuthorResponse(UUID userId, String handle, String nickname, String profileImageUrl) {}
  public record ReactionCountResponse(String emoji, int count) {}
}
