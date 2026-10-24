package org.example.howareyou.domain.chat.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatRoomResponse {
  private String uuid;
  private String roomStatus;
  private Long memberId;    // 1:1 상대방 (그룹방이면 null)
  private String memberName;

  /* 그룹 채팅을 위해 추가 */
  private String roomType;  // ONE_TO_ONE / GROUP
  private String title;     // 그룹방 이름 (1:1이면 null)
  private List<ChatRoomMemberResponse> members; // 그룹방 JOINED 참여자 (1:1이면 null)

  public ChatRoomResponse(String uuid, String roomStatus, Long memberId, String memberName) {
    this.uuid = uuid;
    this.roomStatus = roomStatus;
    this.memberId = memberId;
    this.memberName = memberName;
  }


}
