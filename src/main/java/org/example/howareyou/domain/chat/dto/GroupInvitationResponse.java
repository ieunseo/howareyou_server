package org.example.howareyou.domain.chat.dto;

import java.time.LocalDateTime;

// 내가 받은 그룹 초대 한 건
public record GroupInvitationResponse(
    String roomUuid,
    String title,
    String ownerName,      // 방장 이름
    long joinedMemberCount,
    LocalDateTime invitedAt // 초대 행 생성 시각
) {}
