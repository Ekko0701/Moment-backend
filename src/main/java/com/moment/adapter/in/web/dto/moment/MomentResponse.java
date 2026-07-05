package com.moment.adapter.in.web.dto.moment;

import java.util.List;
import java.util.UUID;

public record MomentResponse(
    UUID id,
    UUID spaceId,
    AuthorResponse author,
    String imageUrl,
    String text,
    long createdAtMs,
    List<ReactionCountResponse> reactions,
    String myReaction) {

  public record AuthorResponse(UUID userId, String handle, String nickname, String profileImageUrl) {}
  public record ReactionCountResponse(String emoji, int count) {}
}
