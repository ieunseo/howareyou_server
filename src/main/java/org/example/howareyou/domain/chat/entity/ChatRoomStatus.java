package org.example.howareyou.domain.chat.entity;

public enum ChatRoomStatus {
  PENDING,    // 요청 보낸 상태 (대기)
  ACCEPTED,   // 수락됨 (채팅 가능)
  REJECTED,    // 거절됨
  ACTIVE, // 그룹방 운영중
  CLOSED // 그룹방 인원없음(종료)
}
