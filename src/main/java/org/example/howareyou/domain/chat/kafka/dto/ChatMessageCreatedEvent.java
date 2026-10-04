package org.example.howareyou.domain.chat.kafka.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChatMessageCreatedEvent {
  private Long roomId;
  private String roomUuid;
  private Long senderId;
  private String senderName;
  private String messageId;    // Mongo 문서 ID로 그대로 사용 (ACK와 저장본의 ID 통일)
  private String content;
  private Instant messageTime; // ACK와 저장본의 시각 통일
  // 단일 receiverId 제거: 수신자는 Consumer에서 방의 JOINED 참여자로 계산
}
