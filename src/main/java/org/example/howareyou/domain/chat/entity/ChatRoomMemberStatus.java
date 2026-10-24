package org.example.howareyou.domain.chat.entity;

public enum ChatRoomMemberStatus {
  JOINED, SENDER, RECEIVER, REJECTED,
  INVITED,//그룹에 초대 받았지만 아직수락전
  LEFT//그룹에서 나감
}
