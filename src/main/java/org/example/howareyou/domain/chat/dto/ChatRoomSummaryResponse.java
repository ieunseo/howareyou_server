package org.example.howareyou.domain.chat.dto;

import java.time.Instant;

public record ChatRoomSummaryResponse(
    String chatRoomId,
    Long opponentId,          // 그룹방이면 null
    String opponentName,      // 그룹방이면 null
    String roomStatus,
    String lastMessageContent,
    Instant lastMessageTime,
    int unreadCount,
    /* 그룹 채팅을 위해 추가 */
    String roomType,          // ONE_TO_ONE / GROUP
    String title,             // 그룹방 이름 (1:1이면 null)
    long memberCount          // JOINED 인원 수
) {}
