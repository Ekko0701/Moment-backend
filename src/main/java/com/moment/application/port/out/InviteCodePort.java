package com.moment.application.port.out;

import com.moment.domain.invite.InviteCode;
import java.util.Optional;
import java.util.UUID;

public interface InviteCodePort {

    InviteCode save(InviteCode code);

    Optional<InviteCode> findByCode(String code);

    void deleteAllByIssuerUserId(UUID issuerUserId);
}
