package org.example.howareyou.domain.chat.dto;

import java.time.LocalDateTime;

// 그룹방 참여자 한 명
public record ChatRoomMemberResponse(
    Long memberId,
    String membername,
    String role,
    LocalDateTime joinedAt
) {}
